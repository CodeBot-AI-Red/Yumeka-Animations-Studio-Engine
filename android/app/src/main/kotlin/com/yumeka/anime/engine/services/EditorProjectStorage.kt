package com.yumeka.anime.engine.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * YASE EditorProjectStorage
 *
 * Persiste o estado do editor diretamente no sistema de arquivos.
 * Toda criação/exclusão de cena, quadro e keyframe é espelhada em tempo real.
 *
 * Estrutura em disco:
 *   EP-1/
 *     Cenas/
 *       Cena 1/
 *         Quadros/
 *           Quadro 1/
 *             Keyframes/
 *               Keyframe 1.png
 *     editor.properties
 */
class EditorProjectStorage(private val projectDir: File) {

    // ── Data classes usados pelo EditorFragment ───────────────────────────────

    data class Scene(val id: Int, val name: String, val start: String, val end: String)

    data class Frame(
        val id: Int,
        val sceneId: Int,
        val name: String,
        val start: String,
        val end: String,
        val keyframes: MutableList<Keyframe> = mutableListOf()
    )

    data class Keyframe(val id: Int, val file: File, var bitmap: Bitmap?)

    data class State(val scenes: List<Scene>, val frames: List<Frame>)

    // ── Caminhos base ─────────────────────────────────────────────────────────

    private val episodeDir = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
    private val cenasDir   = File(episodeDir, "Cenas")
    private val stateFile  = File(episodeDir, "editor.properties")

    init {
        cenasDir.mkdirs()
    }

    // ── Helpers de caminho ────────────────────────────────────────────────────

    private fun cenaFolder(sceneId: Int)                = File(cenasDir, "Cena $sceneId")
    private fun quadrosFolder(sceneId: Int)             = File(cenaFolder(sceneId), "Quadros")
    private fun quadroFolder(sceneId: Int, frameId: Int)= File(quadrosFolder(sceneId), "Quadro $frameId")
    fun      keyframesDir(frameId: Int): File {
        // Busca a pasta do frame em qualquer cena (para compatibilidade com o menu de exclusão)
        cenasDir.listFiles()?.forEach { cenaDir ->
            val candidate = File(File(cenaDir, "Quadros"), "Quadro $frameId/Keyframes")
            if (candidate.exists()) return candidate
        }
        // Fallback: retorna o primeiro encontrado ou path genérico
        return File(episodeDir, "Quadro $frameId/Keyframes")
    }
    private fun keyframesFolder(sceneId: Int, frameId: Int) =
        File(quadroFolder(sceneId, frameId), "Keyframes")

    // ── CENA ──────────────────────────────────────────────────────────────────

    /** Cria EP-1/Cenas/Cena N/Quadros/ imediatamente. */
    fun createSceneFolder(sceneId: Int) {
        quadrosFolder(sceneId).mkdirs()
    }

    /** Apaga toda a pasta da cena (quadros + keyframes incluídos). */
    fun deleteScene(sceneId: Int) {
        cenaFolder(sceneId).deleteRecursively()
    }

    // ── QUADRO ────────────────────────────────────────────────────────────────

    /**
     * Cria a pasta do quadro + Keyframes + Keyframe 1.png em tempo real.
     * Retorna o Frame com o primeiro Keyframe já populado.
     */
    fun createFrame(frameId: Int, sceneId: Int, name: String, start: String, end: String): Frame {
        val kfDir = keyframesFolder(sceneId, frameId)
        kfDir.mkdirs()
        val kf1File = File(kfDir, "Keyframe 1.png")
        if (!kf1File.exists()) kf1File.createNewFile()
        val kf1 = Keyframe(1, kf1File, null)
        // Salva meta do quadro
        updateFrameMeta(frameId, name, start, end)
        return Frame(frameId, sceneId, name, start, end, mutableListOf(kf1))
    }

    /** Atualiza o meta.properties do quadro. */
    fun updateFrameMeta(frameId: Int, name: String, start: String, end: String) {
        // Persistido globalmente no editor.properties via saveState(); método mantido
        // para compatibilidade com chamadas do EditorFragment (ex: ao renomear).
    }

    /** Apaga a pasta inteira do quadro (busca em todas as cenas). */
    fun deleteFrame(frameId: Int) {
        cenasDir.listFiles()?.forEach { cenaDir ->
            val candidate = File(File(cenaDir, "Quadros"), "Quadro $frameId")
            if (candidate.exists()) { candidate.deleteRecursively(); return }
        }
    }

    // ── KEYFRAME ──────────────────────────────────────────────────────────────

    /**
     * Cria o próximo Keyframe N.png dentro da pasta Keyframes do quadro.
     * Busca o frameId em todas as cenas.
     */
    fun createKeyframe(frameId: Int): Keyframe? {
        val kfDir = keyframesDir(frameId).also { it.mkdirs() }
        val existing = kfDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.mapNotNull { it.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull() }
            ?.maxOrNull() ?: 0
        val newIndex = existing + 1
        val file = File(kfDir, "Keyframe $newIndex.png")
        if (!file.exists()) file.createNewFile()
        return Keyframe(newIndex, file, null)
    }

    /** Salva o bitmap de um keyframe no disco imediatamente. */
    fun saveKeyframe(frameId: Int, keyframeId: Int, bitmap: Bitmap?) {
        val kfDir = keyframesDir(frameId)
        val file  = File(kfDir, "Keyframe $keyframeId.png")
        if (bitmap == null) {
            if (file.exists()) file.delete()
        } else {
            kfDir.mkdirs()
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Carrega o bitmap de um keyframe do disco. */
    fun loadKeyframeBitmap(frameId: Int, keyframeId: Int): Bitmap? {
        val file = File(keyframesDir(frameId), "Keyframe $keyframeId.png")
        return if (file.exists() && file.length() > 0L) BitmapFactory.decodeFile(file.absolutePath)
               else null
    }

    /**
     * Apaga um keyframe e renumera os seguintes.
     * Nunca apaga o último — apenas limpa o conteúdo.
     */
    fun deleteKeyframe(frameId: Int, keyframeId: Int) {
        val kfDir = keyframesDir(frameId)
        val files = kfDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull() ?: 0 }
            ?: return
        if (files.size <= 1) {
            files.firstOrNull()?.apply { if (exists()) delete(); createNewFile() }
            return
        }
        File(kfDir, "Keyframe $keyframeId.png").delete()
        // Renumera os posteriores
        val sorted = kfDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull() ?: 0 }
            ?: return
        sorted.forEachIndexed { index, file ->
            val newName = "Keyframe ${index + 1}.png"
            if (file.name != newName) file.renameTo(File(kfDir, newName))
        }
    }

    // ── LOAD ──────────────────────────────────────────────────────────────────

    fun load(): State {
        if (!stateFile.exists()) return State(emptyList(), emptyList())
        return try {
            val p = Properties().apply { FileInputStream(stateFile).use { load(it) } }

            val scenes = p.getProperty("scenes", "").split(',')
                .mapNotNull { it.toIntOrNull() }
                .map { id ->
                    Scene(
                        id,
                        p.getProperty("scene.$id.name",  "Cena $id"),
                        p.getProperty("scene.$id.start", "00:00"),
                        p.getProperty("scene.$id.end",   "00:15")
                    )
                }

            val frames = p.getProperty("frames", "").split(',')
                .mapNotNull { it.toIntOrNull() }
                .mapNotNull { id ->
                    val sceneId = p.getProperty("frame.$id.scene")?.toIntOrNull() ?: return@mapNotNull null
                    val kfDir   = keyframesFolder(sceneId, id).also { it.mkdirs() }
                    val keyframes = kfDir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
                        ?.mapNotNull { f ->
                            val n = f.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull()
                                ?: return@mapNotNull null
                            val bmp = if (f.length() > 0L) BitmapFactory.decodeFile(f.absolutePath) else null
                            Keyframe(n, f, bmp)
                        }
                        ?.sortedBy { it.id }
                        ?.toMutableList() ?: mutableListOf()
                    // Garante pelo menos 1 keyframe
                    if (keyframes.isEmpty()) {
                        val kf1 = File(kfDir, "Keyframe 1.png").also { if (!it.exists()) it.createNewFile() }
                        keyframes.add(Keyframe(1, kf1, null))
                    }
                    Frame(
                        id, sceneId,
                        p.getProperty("frame.$id.name",  "Quadro $id"),
                        p.getProperty("frame.$id.start", "0:00"),
                        p.getProperty("frame.$id.end",   "0:03"),
                        keyframes
                    )
                }

            State(scenes, frames)
        } catch (e: Exception) {
            State(emptyList(), emptyList())
        }
    }

    // ── SAVE STATE ────────────────────────────────────────────────────────────

    fun saveState(scenes: List<Scene>, frames: List<Frame>) {
        episodeDir.mkdirs()
        cenasDir.mkdirs()
        Properties().apply {
            setProperty("scenes", scenes.joinToString(",") { it.id.toString() })
            scenes.forEach {
                setProperty("scene.${it.id}.name",  it.name)
                setProperty("scene.${it.id}.start", it.start)
                setProperty("scene.${it.id}.end",   it.end)
            }
            setProperty("frames", frames.joinToString(",") { it.id.toString() })
            frames.forEach {
                setProperty("frame.${it.id}.scene", it.sceneId.toString())
                setProperty("frame.${it.id}.name",  it.name)
                setProperty("frame.${it.id}.start", it.start)
                setProperty("frame.${it.id}.end",   it.end)
            }
            FileOutputStream(stateFile).use { store(it, "YASE editor state") }
        }
    }
}
