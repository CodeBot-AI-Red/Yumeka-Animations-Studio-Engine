package com.yumeka.anime.engine.ai

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * Ponte JNI para os motores nativos, compilados do codigo-fonte oficial em CI
 * (veja android/native-ai/README.md):
 *  - libyumeka_sd.so   -> stable-diffusion.cpp (DreamShaper XL GGUF)
 *  - libyumeka_vlm.so  -> llama.cpp + mtmd (Moondream2 GGUF)
 * Se a biblioteca nao puder ser carregada, [available] e false e [loadError] traz o motivo real.
 * Nada e simulado: toda saida vem do motor nativo; erros chegam como [NativeAiException].
 */
object NativeDiffusion {
    var loadError: String? = null; private set
    val available: Boolean = try { System.loadLibrary("yumeka_sd"); true } catch (t: Throwable) { loadError = t.message ?: t.toString(); false }

    @JvmStatic external fun nativeSystemInfo(): String
    /** Retorna um handle (>0) ou lanca [NativeAiException]. */
    @JvmStatic external fun nativeLoad(modelPath: String, threads: Int, preferGpu: Boolean): Long
    /** Retorna pixels ARGB (width*height) ou lanca [NativeAiException]. [progress] recebe (passo, total). */
    @JvmStatic external fun nativeGenerate(
        handle: Long, prompt: String, negative: String, width: Int, height: Int,
        steps: Int, guidance: Float, seed: Long, progress: ProgressCallback?
    ): IntArray?
    /** Pode ser chamado de outra thread durante [nativeGenerate]. */
    @JvmStatic external fun nativeCancel(handle: Long)
    @JvmStatic external fun nativeFree(handle: Long)
}

object NativeVision {
    var loadError: String? = null; private set
    val available: Boolean = try { System.loadLibrary("yumeka_vlm"); true } catch (t: Throwable) { loadError = t.message ?: t.toString(); false }

    @JvmStatic external fun nativeSystemInfo(): String
    @JvmStatic external fun nativeLoad(textModelPath: String, mmprojPath: String, threads: Int): Long
    /** Recebe pixels ARGB e retorna a resposta textual do modelo. */
    @JvmStatic external fun nativeAsk(handle: Long, pixels: IntArray, width: Int, height: Int, question: String, maxTokens: Int): String?
    @JvmStatic external fun nativeCancel(handle: Long)
    @JvmStatic external fun nativeFree(handle: Long)
}

fun interface ProgressCallback { fun onProgress(step: Int, total: Int) }

/** Erro real vindo do motor nativo. Codigos espelham native-ai/common/yumeka_jni_common.h. */
class NativeAiException(val code: Int, message: String) : Exception(message) {
    val kind: Kind get() = Kind.entries.firstOrNull { it.code == code } ?: Kind.RUNTIME
    enum class Kind(val code: Int, val title: String) {
        RUNTIME(1, "Erro no motor de IA"),
        MODEL(2, "Erro no modelo"),
        OUT_OF_MEMORY(3, "Memoria insuficiente"),
        CANCELLED(4, "Cancelado"),
        INVALID_ARG(5, "Parametros invalidos")
    }
}

class LocalAiUnavailable(message: String) : Exception(message)

const val UNSUPPORTED_MESSAGE =
    "Seu dispositivo ou navegador nao oferece suporte suficiente para executar este modelo localmente. " +
    "Voce pode tentar usar outro navegador compativel ou utilizar a geracao em nuvem, caso esteja habilitada."

fun unavailableMessage(libError: String?) = UNSUPPORTED_MESSAGE +
    (libError?.let { "\n\nMotivo tecnico: o motor nativo nao foi carregado ($it)." } ?: "")

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
 * Registra quando cada motor executou de verdade neste aparelho. "Compativel" so e
 * exibido depois de uma execucao real bem-sucedida (nao basta o APK ter a biblioteca).
 */
object RuntimeValidation {
    private const val PREFS = "yumeka_ai_runtime"
    fun markOk(ctx: Context, runtimeId: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(runtimeId, System.currentTimeMillis()).apply()
    fun isValidated(ctx: Context, runtimeId: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(runtimeId, 0L) > 0L
    fun clear(ctx: Context, runtimeId: String) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(runtimeId).apply()
}

/**
 * Garante UM modelo carregado por vez (essencial em celulares com pouca memoria):
 * carregar o Moondream2 descarrega o gerador e vice-versa.
 */
object AiSession {
    @Volatile private var sdHandle = 0L
    @Volatile private var vlmHandle = 0L

    @Synchronized
    fun generate(mm: ModelManager, p: GenParams, onState: (ModelState) -> Unit, onStep: (Int, Int) -> Unit): Bitmap {
        if (!NativeDiffusion.available) throw LocalAiUnavailable(unavailableMessage(NativeDiffusion.loadError))
        val spec = Models.DREAMSHAPER
        if (!mm.isInstalled(spec)) throw LocalAiUnavailable("O modelo gerador ainda nao esta instalado.")
        unloadVision()
        if (sdHandle == 0L) {
            onState(ModelState.LOADING)
            sdHandle = NativeDiffusion.nativeLoad(mm.file(spec, spec.files[0]).absolutePath, threads(), false)
        }
        onState(ModelState.READY)
        val prompt = if (p.style.isBlank() || p.style == "Nenhum") p.prompt else "${p.prompt}, ${p.style} style"
        val px = NativeDiffusion.nativeGenerate(sdHandle, prompt, p.negative, p.width, p.height, p.steps, p.guidance, p.seed) { s, t -> onStep(s, t) }
            ?: throw NativeAiException(NativeAiException.Kind.OUT_OF_MEMORY.code, "Sem memoria para receber a imagem gerada.")
        if (px.size != p.width * p.height) throw NativeAiException(1, "O motor retornou uma imagem invalida.")
        mm.markRuntimeOk(spec)
        return Bitmap.createBitmap(px, p.width, p.height, Bitmap.Config.ARGB_8888)
    }

    @Synchronized
    fun ask(mm: ModelManager, bmp: Bitmap, questions: List<String>, onState: (ModelState) -> Unit, onAnswer: (Int) -> Unit): List<String> {
        if (!NativeVision.available) throw LocalAiUnavailable(unavailableMessage(NativeVision.loadError))
        val spec = Models.MOONDREAM2
        if (!mm.isInstalled(spec)) throw LocalAiUnavailable("O Moondream2 ainda nao esta instalado.")
        unloadDiffusion()
        try {
            if (vlmHandle == 0L) {
                onState(ModelState.LOADING)
                vlmHandle = NativeVision.nativeLoad(mm.file(spec, spec.files[0]).absolutePath, mm.file(spec, spec.files[1]).absolutePath, threads())
            }
            onState(ModelState.READY)
            val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val out = questions.mapIndexed { i, q ->
                (NativeVision.nativeAsk(vlmHandle, px, bmp.width, bmp.height, q, 400) ?: "").trim().also { onAnswer(i + 1) }
            }
            mm.markRuntimeOk(spec)
            return out
        } finally {
            unloadVision() // terminou (ou falhou): libera para o proximo modelo
        }
    }

    /** Cancela a tarefa em andamento sem esperar o lock (chamado da thread de UI). */
    fun cancelAll() {
        sdHandle.takeIf { it != 0L }?.let { NativeDiffusion.nativeCancel(it) }
        vlmHandle.takeIf { it != 0L }?.let { NativeVision.nativeCancel(it) }
    }

    @Synchronized fun unloadDiffusion() { val h = sdHandle; if (h != 0L) { sdHandle = 0; NativeDiffusion.nativeFree(h) } }
    @Synchronized fun unloadVision() { val h = vlmHandle; if (h != 0L) { vlmHandle = 0; NativeVision.nativeFree(h) } }
    @Synchronized fun releaseAll() { unloadDiffusion(); unloadVision() }
    val anyLoaded get() = sdHandle != 0L || vlmHandle != 0L

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
