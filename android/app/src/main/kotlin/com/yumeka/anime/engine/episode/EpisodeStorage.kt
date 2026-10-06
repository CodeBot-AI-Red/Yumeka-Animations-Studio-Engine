package com.yumeka.anime.engine.episode

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Armazenamento 100% local do episodio, dentro da pasta do projeto:
 *   <projeto>/Episodio/episode.json
 *   <projeto>/Episodio/midia/...        (audios, videos, imagens importadas)
 *   <projeto>/Episodio/quadros-ia/...   (quadros gerados + metadados .json)
 * Nada e enviado para servidores.
 */
class EpisodeStorage(projectDir: File) {

    val root = File(projectDir, "Episodio").apply { mkdirs() }
    val mediaDir = File(root, "midia").apply { mkdirs() }
    val framesDir = File(root, "quadros-ia").apply { mkdirs() }
    val exportDir = File(projectDir, "Exportados").apply { mkdirs() }
    private val file = File(root, "episode.json")
    private val backup = File(root, "episode.bak.json")

    fun load(defaultName: String): Episode {
        for (f in listOf(file, backup)) {
            if (!f.exists()) continue
            val ep = runCatching { Episode.fromJson(JSONObject(f.readText())) }.getOrNull()
            if (ep != null) return ep
        }
        return Episode(defaultName)
    }

    /** Escrita atomica: grava em .tmp e renomeia, mantendo um backup da versao anterior. */
    @Synchronized
    fun save(ep: Episode) {
        val tmp = File(root, "episode.tmp")
        tmp.writeText(ep.toJson().toString(2))
        if (file.exists()) file.copyTo(backup, overwrite = true)
        if (!tmp.renameTo(file)) { tmp.copyTo(file, overwrite = true); tmp.delete() }
    }

    /** Copia um arquivo escolhido pelo usuario para dentro do projeto. */
    fun importUri(ctx: Context, uri: Uri, prefix: String): File {
        val name = displayName(ctx, uri) ?: "${prefix}_${System.currentTimeMillis()}"
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dest = uniqueFile(mediaDir, "${prefix}_$safe")
        val input = ctx.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Nao foi possivel abrir o arquivo selecionado.")
        input.use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
        if (dest.length() == 0L) { dest.delete(); throw IllegalStateException("O arquivo selecionado esta vazio.") }
        return dest
    }

    fun saveFrame(bmp: Bitmap, meta: FrameMeta): File {
        val f = uniqueFile(framesDir, "quadro_${meta.createdAt}_${meta.seed}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(framesDir, f.nameWithoutExtension + ".json").writeText(meta.toJson().toString(2))
        return f
    }

    fun updateFrameMeta(frame: File, meta: FrameMeta) {
        File(frame.parentFile, frame.nameWithoutExtension + ".json").writeText(meta.toJson().toString(2))
    }

    /** Pacote completo (.zip) com o episodio e todas as midias, para backup/transferencia. */
    fun exportPackage(ep: Episode, onProgress: (Int) -> Unit): File {
        save(ep)
        val out = uniqueFile(exportDir, "${ep.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_episodio.zip")
        val files = root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            files.forEachIndexed { i, f ->
                zip.putNextEntry(ZipEntry(f.relativeTo(root).path))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                onProgress(((i + 1) * 100) / files.size.coerceAtLeast(1))
            }
        }
        return out
    }

    fun deleteMediaIfUnused(ep: Episode, path: String) {
        if (path.isBlank() || ep.clips.any { it.file == path }) return
        val f = File(path)
        if (f.absolutePath.startsWith(root.absolutePath)) f.delete()
    }

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    companion object {
        fun uniqueFile(dir: File, name: String): File {
            var f = File(dir, name); var n = 1
            val base = name.substringBeforeLast('.'); val ext = name.substringAfterLast('.', "")
            while (f.exists()) { f = File(dir, if (ext.isEmpty()) "${base}_$n" else "${base}_$n.$ext"); n++ }
            return f
        }
    }
}

/** Desfazer/refazer por instantaneos JSON do episodio. */
class EpisodeHistory(private val limit: Int = 60) {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    fun push(ep: Episode) {
        undo.addLast(ep.toJson().toString())
        if (undo.size > limit) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: Episode): Episode? {
        val prev = undo.removeLastOrNull() ?: return null
        redo.addLast(current.toJson().toString())
        return Episode.fromJson(JSONObject(prev))
    }

    fun redo(current: Episode): Episode? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current.toJson().toString())
        return Episode.fromJson(JSONObject(next))
    }

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
}
