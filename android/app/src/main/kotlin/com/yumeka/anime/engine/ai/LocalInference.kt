package com.yumeka.anime.engine.ai

import android.graphics.Bitmap
import java.io.File

/**
 * Ponte JNI para os motores nativos. As bibliotecas sao opcionais:
 *  - libyumeka_sd.so   -> stable-diffusion.cpp (DreamShaper XL GGUF)
 *  - libyumeka_vlm.so  -> llama.cpp/mtmd (Moondream2 GGUF)
 * Se a biblioteca nao estiver empacotada no APK, [available] e false e o app
 * informa claramente que a execucao local nao e possivel — nunca simula resultado.
 * Veja android/app/src/main/cpp/ai/README.md para integrar os motores.
 */
object NativeDiffusion {
    val available: Boolean = try { System.loadLibrary("yumeka_sd"); true } catch (_: Throwable) { false }

    /** Retorna um handle (>0) ou 0 em caso de falha. */
    @JvmStatic external fun nativeLoad(modelPath: String, threads: Int, preferGpu: Boolean): Long
    /** Retorna pixels ARGB (width*height) ou null. [progress] recebe (passo, total). */
    @JvmStatic external fun nativeGenerate(
        handle: Long, prompt: String, negative: String, width: Int, height: Int,
        steps: Int, guidance: Float, seed: Long, progress: ProgressCallback
    ): IntArray?
    @JvmStatic external fun nativeFree(handle: Long)
}

object NativeVision {
    val available: Boolean = try { System.loadLibrary("yumeka_vlm"); true } catch (_: Throwable) { false }

    @JvmStatic external fun nativeLoad(textModelPath: String, mmprojPath: String, threads: Int): Long
    /** Recebe pixels ARGB e retorna a resposta textual do modelo. */
    @JvmStatic external fun nativeAsk(handle: Long, pixels: IntArray, width: Int, height: Int, question: String, maxTokens: Int): String?
    @JvmStatic external fun nativeFree(handle: Long)
}

fun interface ProgressCallback { fun onProgress(step: Int, total: Int) }

class LocalAiUnavailable(message: String) : Exception(message)

const val UNSUPPORTED_MESSAGE =
    "Seu dispositivo ou navegador nao oferece suporte suficiente para executar este modelo localmente. " +
    "Voce pode tentar usar outro navegador compativel ou utilizar a geracao em nuvem, caso esteja habilitada."

data class GenParams(
    val prompt: String,
    val negative: String,
    val style: String,
    val width: Int,
    val height: Int,
    val steps: Int,
    val guidance: Float,
    val seed: Long
)

/**
 * Garante UM modelo carregado por vez (essencial em celulares com pouca memoria):
 * carregar o Moondream2 descarrega o gerador e vice-versa.
 */
object AiSession {
    private var sdHandle = 0L
    private var vlmHandle = 0L

    @Synchronized
    fun generate(mm: ModelManager, p: GenParams, onState: (ModelState) -> Unit, onStep: (Int, Int) -> Unit): Bitmap {
        if (!NativeDiffusion.available) throw LocalAiUnavailable(UNSUPPORTED_MESSAGE)
        val spec = Models.DREAMSHAPER
        if (!mm.isInstalled(spec)) throw LocalAiUnavailable("O modelo gerador ainda nao esta instalado.")
        unloadVision()
        if (sdHandle == 0L) {
            onState(ModelState.LOADING)
            sdHandle = NativeDiffusion.nativeLoad(mm.file(spec, spec.files[0]).absolutePath, threads(), true)
            if (sdHandle == 0L) throw IllegalStateException("Falha ao carregar o modelo na memoria. Feche outros apps e tente novamente.")
        }
        onState(ModelState.READY)
        val prompt = if (p.style.isBlank() || p.style == "Nenhum") p.prompt else "${p.prompt}, ${p.style} style"
        val px = NativeDiffusion.nativeGenerate(sdHandle, prompt, p.negative, p.width, p.height, p.steps, p.guidance, p.seed) { s, t -> onStep(s, t) }
            ?: throw IllegalStateException("A geracao falhou. Tente uma resolucao menor.")
        if (px.size != p.width * p.height) throw IllegalStateException("O motor retornou uma imagem invalida.")
        return Bitmap.createBitmap(px, p.width, p.height, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun ask(mm: ModelManager, bmp: Bitmap, questions: List<String>, onState: (ModelState) -> Unit, onAnswer: (Int) -> Unit): List<String> {
        if (!NativeVision.available) throw LocalAiUnavailable(UNSUPPORTED_MESSAGE)
        val spec = Models.MOONDREAM2
        if (!mm.isInstalled(spec)) throw LocalAiUnavailable("O Moondream2 ainda nao esta instalado.")
        unloadDiffusion()
        if (vlmHandle == 0L) {
            onState(ModelState.LOADING)
            vlmHandle = NativeVision.nativeLoad(mm.file(spec, spec.files[0]).absolutePath, mm.file(spec, spec.files[1]).absolutePath, threads())
            if (vlmHandle == 0L) throw IllegalStateException("Falha ao carregar o Moondream2 na memoria.")
        }
        onState(ModelState.READY)
        val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val out = questions.mapIndexed { i, q ->
            (NativeVision.nativeAsk(vlmHandle, px, bmp.width, bmp.height, q, 400) ?: "").trim().also { onAnswer(i + 1) }
        }
        unloadVision() // terminou a tarefa: libera para o proximo modelo
        return out
    }

    @Synchronized fun unloadDiffusion() { if (sdHandle != 0L) { NativeDiffusion.nativeFree(sdHandle); sdHandle = 0 } }
    @Synchronized fun unloadVision() { if (vlmHandle != 0L) { NativeVision.nativeFree(vlmHandle); vlmHandle = 0 } }
    @Synchronized fun releaseAll() { unloadDiffusion(); unloadVision() }

    private fun threads() = (Runtime.getRuntime().availableProcessors() - 2).coerceIn(2, 6)
}

/**
 * Geracao em nuvem (opcional). Por seguranca, nenhuma chave secreta fica no app:
 * a nuvem so pode ser habilitada apontando para um servidor proprio que guarde as chaves.
 * Enquanto [endpoint] estiver vazio, a nuvem fica desativada e o app informa isso.
 */
object CloudGeneration {
    var endpoint: String = ""
    val enabled get() = endpoint.isNotBlank()
}

/** Prompt interno usado na analise do quadro. */
const val ANALYSIS_PROMPT =
    "Analise este quadro de forma objetiva. Verifique se o personagem, rosto, olhos, maos, corpo, objetos, cenario, " +
    "iluminacao e composicao estao coerentes. Compare a imagem com o prompt original. Liste os problemas encontrados " +
    "e crie uma sugestao de prompt corrigido."

fun analysisQuestions(originalPrompt: String) = listOf(
    "Describe this image in detail.",
    "List every object, person and character visible in this image, separated by commas.",
    "Is there a person or character in this image? Are there visual deformities in face, eyes, hands or body? Answer briefly.",
    "Rate the overall visual quality and composition of this image from 1 to 10. Answer with the number and one short reason.",
    "$ANALYSIS_PROMPT\nPrompt original: \"$originalPrompt\"\n" +
        "Responda no formato:\nPROBLEMAS: ...\nSUGESTOES: ...\nPROMPT CORRIGIDO: ..."
)

fun File.sizeOk(expected: Long) = exists() && length() == expected
