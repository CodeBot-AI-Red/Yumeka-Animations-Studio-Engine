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
 * Persiste o estado do editor diretamente no sistema de arquivos do projeto,
 * sem depender de memória do app. Toda criação/exclusão de quadro e keyframe
 * é espelhada em tempo real na estrutura de pastas.
 *
 * Estrutura gerada:
 *   EP-1/
 *     Quadros/
 *       Quadro 1/
 *         Keyframes/
 *           Keyframe 1.png
 *           Keyframe 2.png
 *           ...
 *       Quadro 2/
 *         Keyframes/
 *           Keyframe 1.png
 *           ...
 *     editor.properties   ← estado serializado
 */
class EditorProjectStorage(private val projectDir: File) {

    data class Scene(val id: Int, val name: String, val start: String, val end: String)

    /**
     * Um Frame (quadro) pode ter 1..N keyframes.
     * [keyframeCount] é o total salvo em disco; [artwork] é o bitmap do
     * keyframe actualmente seleccionado (carregado sob demanda).
     */
    data class Frame(
        val id: Int,
        val sceneId: Int,
        val name: String,
        val start: String,
        val end: String,
        val keyframeCount: Int = 1,
        val artwork: Bitmap? = null
    )

    data class State(val scenes: List<Scene>, val frames: List<Frame>)

    // ── Caminhos base ────────────────────────────────────────────────────────
    private val episodeDir  = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
    private val framesDir   = File(episodeDir,  "Quadros")
    private val stateFile   = File(episodeDir,  "editor.properties")

    // ── Inicialização ────────────────────────────────────────────────────────
    init {
        framesDir.mkdirs()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LOAD
    // ════════════════════════════════════════════════════════════════════════
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
                    val kfCount = p.getProperty("frame.$id.keyframe_count", "1").toIntOrNull() ?: 1
                    val folder  = quadroFolder(id)
                    // Garante que a pasta existe mesmo que tenha sido apagada externamente
                    ensureKeyframesFolder(folder)
                    // Carrega o 1.º keyframe como bitmap de pré-visualização
                    val artwork = loadKeyframe(folder, 1)
                    Frame(id, sceneId,
                        p.getProperty("frame.$id.name",  "Quadro $id"),
                        p.getProperty("frame.$id.start", "0:00"),
                        p.getProperty("frame.$id.end",   "0:03"),
                        kfCount, artwork
                    )
                }

            State(scenes, frames)
        } catch (e: Exception) {
            State(emptyList(), emptyList())
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SAVE STATE (cenas + metadados de quadros)
    // ════════════════════════════════════════════════════════════════════════
    fun saveState(scenes: List<Scene>, frames: List<Frame>) {
        episodeDir.mkdirs()
        framesDir.mkdirs()
        Properties().apply {
            setProperty("scenes", scenes.joinToString(",") { it.id.toString() })
            scenes.forEach {
                setProperty("scene.${it.id}.name",  it.name)
                setProperty("scene.${it.id}.start", it.start)
                setProperty("scene.${it.id}.end",   it.end)
            }
            setProperty("frames", frames.joinToString(",") { it.id.toString() })
            frames.forEach {
                setProperty("frame.${it.id}.scene",           it.sceneId.toString())
                setProperty("frame.${it.id}.name",            it.name)
                setProperty("frame.${it.id}.start",           it.start)
                setProperty("frame.${it.id}.end",             it.end)
                setProperty("frame.${it.id}.keyframe_count",  it.keyframeCount.toString())
            }
            FileOutputStream(stateFile).use { store(it, "YASE editor state") }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  QUADRO — operações em tempo real no sistema de arquivos
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Chamado imediatamente ao criar um quadro.
     * Cria:
     *   Quadros/Quadro {frameId}/
     *   Quadros/Quadro {frameId}/Keyframes/
     * Retorna o número de keyframes iniciais (sempre 1).
     */
    fun createFrameFolder(frameId: Int): Int {
        val folder = quadroFolder(frameId)
        ensureKeyframesFolder(folder)
        // Cria placeholder do primeiro keyframe (arquivo vazio é suficiente para
        // indicar existência; o bitmap real é gravado quando o usuário desenhar)
        val kf1 = keyframeFile(folder, 1)
        if (!kf1.exists()) kf1.createNewFile()
        return 1
    }

    /**
     * Apaga a pasta inteira do quadro (inclusive todos os keyframes) em tempo real.
     */
    fun deleteFrame(frameId: Int) {
        quadroFolder(frameId).deleteRecursively()
    }

    // ════════════════════════════════════════════════════════════════════════
    //  KEYFRAME — operações em tempo real no sistema de arquivos
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Adiciona um novo keyframe ao quadro (cria o arquivo PNG em disco).
     * Retorna o índice do novo keyframe (1-based).
     */
    fun addKeyframe(frameId: Int, currentCount: Int): Int {
        val newIndex = currentCount + 1
        val folder   = quadroFolder(frameId).also { ensureKeyframesFolder(it) }
        val kfFile   = keyframeFile(folder, newIndex)
        if (!kfFile.exists()) kfFile.createNewFile()
        return newIndex
    }

    /**
     * Salva o bitmap de um keyframe específico em tempo real.
     */
    fun saveKeyframe(frameId: Int, keyframeIndex: Int, artwork: Bitmap?) {
        val folder = quadroFolder(frameId).also { ensureKeyframesFolder(it) }
        val file   = keyframeFile(folder, keyframeIndex)
        if (artwork == null) {
            if (file.exists()) file.delete()
        } else {
            FileOutputStream(file).use { artwork.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    /** Atalho para salvar no keyframe 1 (compatibilidade com o canvas actual). */
    fun saveArtwork(frameId: Int, artwork: Bitmap?) = saveKeyframe(frameId, 1, artwork)

    /**
     * Apaga um keyframe específico e renomeia os seguintes para manter
     * a sequência contínua (Keyframe 2 → Keyframe 1, etc.).
     * Retorna o novo total de keyframes.
     */
    fun deleteKeyframe(frameId: Int, keyframeIndex: Int, currentCount: Int): Int {
        if (currentCount <= 1) {
            // Nunca apaga o último keyframe; apenas limpa o conteúdo
            val file = keyframeFile(quadroFolder(frameId), 1)
            if (file.exists()) file.delete()
            file.createNewFile()
            return 1
        }
        val folder = quadroFolder(frameId)
        keyframeFile(folder, keyframeIndex).delete()
        // Renumera os posteriores
        for (i in (keyframeIndex + 1)..currentCount) {
            val src = keyframeFile(folder, i)
            val dst = keyframeFile(folder, i - 1)
            if (src.exists()) src.renameTo(dst)
        }
        return currentCount - 1
    }

    /**
     * Carrega o bitmap de um keyframe (null se vazio ou inexistente).
     */
    fun loadKeyframe(frameId: Int, keyframeIndex: Int): Bitmap? =
        loadKeyframe(quadroFolder(frameId), keyframeIndex)

    // ════════════════════════════════════════════════════════════════════════
    //  Helpers privados
    // ════════════════════════════════════════════════════════════════════════

    private fun quadroFolder(id: Int)  = File(framesDir, "Quadro $id")
    private fun keyframesFolder(folder: File) = File(folder, "Keyframes")
    private fun keyframeFile(folder: File, index: Int) = File(keyframesFolder(folder), "Keyframe $index.png")
    private fun ensureKeyframesFolder(quadroFolder: File) { keyframesFolder(quadroFolder).mkdirs() }

    private fun loadKeyframe(folder: File, index: Int): Bitmap? {
        val file = keyframeFile(folder, index)
        return if (file.exists() && file.length() > 0L)
            BitmapFactory.decodeFile(file.absolutePath)
        else null
    }
}
