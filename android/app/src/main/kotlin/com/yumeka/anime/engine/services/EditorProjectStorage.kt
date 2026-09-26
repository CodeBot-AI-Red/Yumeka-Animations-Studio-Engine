package com.yumeka.anime.engine.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * EditorProjectStorage — Camada de persistencia em tempo real do YASE.
 *
 * Estrutura de pastas gerenciada:
 *
 *   <projectDir>/
 *     Temporadas/Temporada 1/Episodios/EP-1/
 *       editor.properties          ← metadados de cenas e quadros
 *       Quadros/
 *         Quadro 1/
 *           meta.properties        ← nome, inicio, fim do quadro
 *           Keyframes/
 *             Keyframe 1.png
 *             Keyframe 2.png
 *             ...
 *         Quadro 2/
 *           ...
 *
 * Toda acao (criar, excluir, modificar) e executada imediatamente no disco.
 * Nao ha buffer — o disco e sempre a fonte da verdade.
 */
class EditorProjectStorage(private val projectDir: File) {

    // ── Modelos de dominio ────────────────────────────────────────────────────

    data class Scene(
        val id: Int,
        val name: String,
        val start: String,
        val end: String
    )

    data class Keyframe(
        val id: Int,         // numero do keyframe (1, 2, 3...)
        val file: File,      // arquivo PNG no disco
        var bitmap: Bitmap?  // imagem carregada (nulo = nao carregado ainda)
    )

    data class Frame(
        val id: Int,
        val sceneId: Int,
        val name: String,
        val start: String,
        val end: String,
        val keyframes: MutableList<Keyframe> = mutableListOf()
    )

    data class State(
        val scenes: List<Scene>,
        val frames: List<Frame>   // todos os frames de todas as cenas
    )

    // ── Caminhos base ────────────────────────────────────────────────────────

    private val ep1Dir     = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
    private val framesDir  = File(ep1Dir, "Quadros")
    private val stateFile  = File(ep1Dir, "editor.properties")

    // ── Helpers de caminho ───────────────────────────────────────────────────

    fun frameDir(frameId: Int): File = File(framesDir, "Quadro $frameId")
    fun frameMeta(frameId: Int): File = File(frameDir(frameId), "meta.properties")
    fun keyframesDir(frameId: Int): File = File(frameDir(frameId), "Keyframes")
    fun keyframeFile(frameId: Int, keyframeId: Int): File =
        File(keyframesDir(frameId), "Keyframe $keyframeId.png")

    // ── Carregamento ─────────────────────────────────────────────────────────

    /**
     * Carrega o estado completo do projeto a partir do disco.
     * Fonte de verdade: editor.properties para cenas e IDs de frames;
     * cada Quadro X/meta.properties para metadados do frame;
     * cada Quadro X/Keyframes/*.png para os keyframes reais.
     */
    fun load(): State {
        if (!stateFile.exists()) return State(emptyList(), emptyList())
        return try {
            val p = Properties().apply { FileInputStream(stateFile).use(::load) }

            // --- Cenas
            val scenes = p.getProperty("scenes", "").split(',')
                .mapNotNull { it.toIntOrNull() }
                .map { id ->
                    Scene(
                        id   = id,
                        name = p.getProperty("scene.$id.name", "Cena $id"),
                        start= p.getProperty("scene.$id.start", "00:00"),
                        end  = p.getProperty("scene.$id.end", "00:15")
                    )
                }

            // --- Frames
            val frames = p.getProperty("frames", "").split(',')
                .mapNotNull { it.toIntOrNull() }
                .mapNotNull { fid ->
                    val sceneId = p.getProperty("frame.$fid.scene")?.toIntOrNull() ?: return@mapNotNull null
                    val meta    = loadFrameMeta(fid)
                    val kfs     = loadKeyframes(fid)
                    Frame(
                        id       = fid,
                        sceneId  = sceneId,
                        name     = meta.getProperty("name", "Quadro $fid"),
                        start    = meta.getProperty("start", "0:00"),
                        end      = meta.getProperty("end", "0:03"),
                        keyframes= kfs
                    )
                }

            State(scenes, frames)
        } catch (e: Exception) {
            State(emptyList(), emptyList())
        }
    }

    private fun loadFrameMeta(frameId: Int): Properties {
        val f = frameMeta(frameId)
        return Properties().apply {
            if (f.exists()) FileInputStream(f).use(::load)
        }
    }

    /**
     * Le todos os keyframes existentes no disco para um quadro.
     * Ordena pelo numero no nome do arquivo (Keyframe 1, 2, 3...).
     */
    private fun loadKeyframes(frameId: Int): MutableList<Keyframe> {
        val dir = keyframesDir(frameId)
        if (!dir.exists()) return mutableListOf()
        return dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.mapNotNull { file ->
                val num = file.nameWithoutExtension
                    .removePrefix("Keyframe ").toIntOrNull() ?: return@mapNotNull null
                Keyframe(
                    id     = num,
                    file   = file,
                    bitmap = BitmapFactory.decodeFile(file.absolutePath)
                )
            }
            ?.sortedBy { it.id }
            ?.toMutableList()
            ?: mutableListOf()
    }

    // ── Persistencia de estado global ────────────────────────────────────────

    /** Salva metadados de cenas e IDs de frames no editor.properties. */
    fun saveState(scenes: List<Scene>, frames: List<Frame>) {
        ep1Dir.mkdirs()
        framesDir.mkdirs()
        Properties().apply {
            setProperty("scenes", scenes.joinToString(",") { it.id.toString() })
            scenes.forEach { s ->
                setProperty("scene.${s.id}.name",  s.name)
                setProperty("scene.${s.id}.start", s.start)
                setProperty("scene.${s.id}.end",   s.end)
            }
            setProperty("frames", frames.joinToString(",") { it.id.toString() })
            frames.forEach { f ->
                setProperty("frame.${f.id}.scene", f.sceneId.toString())
            }
            FileOutputStream(stateFile).use { store(it, "YASE editor state") }
        }
    }

    // ── Operacoes em tempo real: Quadros ─────────────────────────────────────

    /**
     * Cria a pasta do quadro e seus metadados imediatamente no disco.
     * Tambem cria a pasta Keyframes/ vazia dentro do quadro.
     * Retorna o Frame criado.
     */
    fun createFrame(frameId: Int, sceneId: Int, name: String, start: String, end: String): Frame {
        val dir = frameDir(frameId)
        val kfDir = keyframesDir(frameId)
        dir.mkdirs()
        kfDir.mkdirs()
        saveFrameMeta(frameId, name, start, end)
        return Frame(id = frameId, sceneId = sceneId, name = name, start = start, end = end)
    }

    /** Atualiza metadados de um quadro (nome, inicio, fim) imediatamente. */
    fun updateFrameMeta(frameId: Int, name: String, start: String, end: String) {
        saveFrameMeta(frameId, name, start, end)
    }

    /** Exclui a pasta inteira do quadro (incluindo todos os keyframes). */
    fun deleteFrame(frameId: Int) {
        frameDir(frameId).deleteRecursively()
    }

    private fun saveFrameMeta(frameId: Int, name: String, start: String, end: String) {
        val metaFile = frameMeta(frameId)
        metaFile.parentFile?.mkdirs()
        Properties().apply {
            setProperty("name",  name)
            setProperty("start", start)
            setProperty("end",   end)
            FileOutputStream(metaFile).use { store(it, "YASE frame meta") }
        }
    }

    // ── Operacoes em tempo real: Keyframes ───────────────────────────────────

    /**
     * Cria um novo keyframe vazio no disco e retorna o objeto Keyframe.
     * O ID e o proximo numero disponivel (max existente + 1).
     */
    fun createKeyframe(frameId: Int): Keyframe {
        val dir = keyframesDir(frameId)
        dir.mkdirs()
        val nextId = nextKeyframeId(frameId)
        val file = keyframeFile(frameId, nextId)
        // Cria arquivo PNG vazio (placeholder ate o usuario desenhar)
        if (!file.exists()) file.createNewFile()
        return Keyframe(id = nextId, file = file, bitmap = null)
    }

    /** Salva o bitmap de um keyframe imediatamente no disco. */
    fun saveKeyframe(frameId: Int, keyframeId: Int, bitmap: Bitmap?) {
        val file = keyframeFile(frameId, keyframeId)
        file.parentFile?.mkdirs()
        if (bitmap == null) {
            // Mantém o arquivo mas vazio — nao exclui ao salvar nulo
            if (!file.exists()) file.createNewFile()
            return
        }
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Exclui um keyframe do disco imediatamente. */
    fun deleteKeyframe(frameId: Int, keyframeId: Int) {
        keyframeFile(frameId, keyframeId).delete()
        // Renumera os keyframes restantes para manter a sequencia continua
        renumberKeyframes(frameId)
    }

    /**
     * Reordena os arquivos de keyframe para que nao haja buracos na numeracao.
     * Ex: apos excluir Keyframe 2, Keyframe 3 passa a ser Keyframe 2.
     */
    private fun renumberKeyframes(frameId: Int) {
        val dir = keyframesDir(frameId)
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.mapNotNull { f ->
                val n = f.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull()
                if (n != null) n to f else null
            }
            ?.sortedBy { it.first } ?: return

        // Renomeia para temporarios primeiro (evita colisoes)
        files.forEach { (_, f) -> f.renameTo(File(dir, "tmp_${f.name}")) }
        // Renomeia para sequencia final
        files.forEachIndexed { index, _ ->
            val tmp = File(dir, "tmp_${files[index].second.name}")
            tmp.renameTo(File(dir, "Keyframe ${index + 1}.png"))
        }
    }

    /** Retorna o proximo ID de keyframe disponivel para um quadro. */
    fun nextKeyframeId(frameId: Int): Int {
        val dir = keyframesDir(frameId)
        if (!dir.exists()) return 1
        val max = dir.listFiles { f -> f.extension.equals("png", ignoreCase = true) }
            ?.mapNotNull { f -> f.nameWithoutExtension.removePrefix("Keyframe ").toIntOrNull() }
            ?.maxOrNull() ?: 0
        return max + 1
    }

    /** Carrega o bitmap de um keyframe especifico do disco. */
    fun loadKeyframeBitmap(frameId: Int, keyframeId: Int): Bitmap? {
        val file = keyframeFile(frameId, keyframeId)
        if (!file.exists() || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
