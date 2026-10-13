package com.yumeka.anime.engine.episode

import android.graphics.Bitmap
import com.yumeka.anime.engine.studio.ART_H
import com.yumeka.anime.engine.studio.ART_W
import com.yumeka.anime.engine.studio.FrameRenderer
import com.yumeka.anime.engine.studio.StudioProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext

/** Immutable snapshot: ordered scenes/frames with their FPS/hold, no edits to Studio layers. */
object StudioSequenceImporter {
    const val SOURCE_KEY = "studio-drawings"
    suspend fun update(project: StudioProject, storage: EpisodeStorage, progress: (Int) -> Unit): Episode {
        val folder = withContext(Dispatchers.IO) {
            File(storage.mediaDir, "desenhos_${java.util.UUID.randomUUID()}").apply {
                check(mkdirs()) { "Nao foi possivel criar a pasta da sequencia" }
            }
        }
        val total = project.scenes.sumOf { it.frames.size }
        val frames = mutableListOf<SequenceFrame>()
        var ticks = 0L
        try {
            check(total > 0) { "Nao ha quadros para adicionar" }
            for (scene in project.scenes) for (idx in scene.frames.indices) {
                coroutineContext.ensureActive()
                // Shared renderer paints stay on Main; file writes yield on IO between frames.
                val bitmap = withContext(Dispatchers.Main) { FrameRenderer.renderBitmap(scene, idx, ART_W, ART_H) }
                val file = File(folder, "quadro_%06d.png".format(frames.size + 1))
                try {
                    withContext(Dispatchers.IO) {
                        FileOutputStream(file).use { out ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Falha ao salvar um quadro" }
                        }
                    }
                } finally { bitmap.recycle() }
                ticks += scene.frames[idx].hold.coerceAtLeast(1)
                val end = (ticks * 1000 / project.fps.coerceIn(1, 60)).coerceAtLeast((frames.lastOrNull()?.endMs ?: 0) + 1)
                frames.add(SequenceFrame(file.absolutePath, end))
                progress(frames.size * 100 / total)
            }
            // Cancellation is honoured before commit. Once saving starts, never delete committed files.
            coroutineContext.ensureActive()
            return withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                val ep = storage.load(project.name)
                val previous = ep.clips.filter { it.sourceKey == SOURCE_KEY }
                val position = previous.minOfOrNull { it.startMs } ?: 0L
                val row = previous.firstOrNull()?.let { ep.clips.indexOf(it) } ?: 0
                ep.clips.removeAll { it.sourceKey == SOURCE_KEY }
                val clip = Clip(ep.newId(), TrackType.SEQUENCE, position, frames.last().endMs,
                    "Animação Studio ($total quadros)", frames = frames.toList(), sourceKey = SOURCE_KEY)
                ep.clips.add(row.coerceAtMost(ep.clips.size), clip)
                ep.playheadMs = position + ep.introMs
                storage.save(ep)
                ep
            }
        } catch (e: Throwable) {
            // A committed snapshot may be referenced by the atomic save or its backup.
            val referenced = withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                storage.load(project.name).clips.any { c -> c.frames.any { File(it.file).parentFile == folder } }
            }
            if (!referenced) withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { folder.deleteRecursively() }
            throw e
        }
    }
}
