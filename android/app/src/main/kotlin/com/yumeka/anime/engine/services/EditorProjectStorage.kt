package com.yumeka.anime.engine.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/** Persiste o editor diretamente dentro do projeto, sem depender de memoria do app. */
class EditorProjectStorage(private val projectDir: File) {
    data class Scene(val id: Int, val name: String, val start: String, val end: String)
    data class Frame(val id: Int, val sceneId: Int, val name: String, val start: String, val end: String, val artwork: Bitmap?)
    data class State(val scenes: List<Scene>, val frames: List<Frame>)

    private val episodeDir = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
    private val framesDir = File(episodeDir, "Quadros")
    private val stateFile = File(episodeDir, "editor.properties")

    fun load(): State {
        if (!stateFile.exists()) return State(emptyList(), emptyList())
        return try {
            val p = Properties().apply { FileInputStream(stateFile).use(::load) }
            val scenes = p.getProperty("scenes", "").split(',').mapNotNull { id ->
                id.toIntOrNull()?.let { value -> Scene(value, p.getProperty("scene.$value.name", "Cena $value"), p.getProperty("scene.$value.start", "00:00"), p.getProperty("scene.$value.end", "00:15")) }
            }
            val frames = p.getProperty("frames", "").split(',').mapNotNull { id ->
                id.toIntOrNull()?.let { value ->
                    val sceneId = p.getProperty("frame.$value.scene")?.toIntOrNull() ?: return@let null
                    val folder = frameFolder(value)
                    Frame(value, sceneId, p.getProperty("frame.$value.name", "Quadro $value"), p.getProperty("frame.$value.start", "0:00"), p.getProperty("frame.$value.end", "0:03"), artworkFile(folder).takeIf(File::exists)?.let(BitmapFactory::decodeFile))
                }
            }
            State(scenes, frames)
        } catch (_: Exception) { State(emptyList(), emptyList()) }
    }

    fun saveState(scenes: List<Scene>, frames: List<Frame>) {
        episodeDir.mkdirs(); framesDir.mkdirs()
        Properties().apply {
            setProperty("scenes", scenes.joinToString(",") { it.id.toString() })
            scenes.forEach { setProperty("scene.${it.id}.name", it.name); setProperty("scene.${it.id}.start", it.start); setProperty("scene.${it.id}.end", it.end) }
            setProperty("frames", frames.joinToString(",") { it.id.toString() })
            frames.forEach { setProperty("frame.${it.id}.scene", it.sceneId.toString()); setProperty("frame.${it.id}.name", it.name); setProperty("frame.${it.id}.start", it.start); setProperty("frame.${it.id}.end", it.end) }
            FileOutputStream(stateFile).use { store(it, "YASE editor state") }
        }
    }

    /** Cada quadro possui a propria pasta e os keyframes ficam nela. */
    fun createFrameFolder(frameId: Int) { artworkFile(frameFolder(frameId).apply { mkdirs() }).parentFile?.mkdirs() }
    fun deleteFrame(frameId: Int) { frameFolder(frameId).deleteRecursively() }
    fun saveArtwork(frameId: Int, artwork: Bitmap?) {
        val file = artworkFile(frameFolder(frameId).apply { mkdirs() })
        if (artwork == null) file.delete() else FileOutputStream(file).use { artwork.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun frameFolder(id: Int) = File(framesDir, "Quadro $id")
    private fun artworkFile(folder: File) = File(folder, "Keyframes/Keyframe 1.png")
}
