package com.yumeka.anime.engine.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

data class ModelFile(val url: String, val fileName: String, val sizeBytes: Long)

data class ModelSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val files: List<ModelFile>,
    /** RAM total minima do aparelho para tentar rodar o modelo. */
    val minRamMb: Long,
    val runtime: String
) {
    val totalBytes get() = files.sumOf { it.sizeBytes }
}

object Models {
    /** DreamShaper XL v2 Turbo (GGUF Q4_K) — roda via stable-diffusion.cpp. */
    val DREAMSHAPER = ModelSpec(
        id = "dreamshaper-xl-v2-turbo-q4k",
        displayName = "DreamShaper XL v2 Turbo (Q4_K)",
        description = "Gerador de quadros. Formato GGUF para stable-diffusion.cpp.",
        files = listOf(
            ModelFile(
                "https://huggingface.co/offgrid-ai/dreamshaper-xl-v2-turbo-GGUF/resolve/main/dreamshaper-xl-v2-turbo-Q4_K.gguf",
                "dreamshaper-xl-v2-turbo-Q4_K.gguf", 2_798_084_896L
            )
        ),
        minRamMb = 6_000,
        runtime = "stable-diffusion.cpp"
    )

    /** Moondream2 (2025-04-14) GGUF — modelo de texto + projetor de visao, roda via llama.cpp (mtmd). */
    val MOONDREAM2 = ModelSpec(
        id = "moondream2-20250414",
        displayName = "Moondream2 (2025-04-14)",
        description = "Analisa quadros: descreve, encontra objetos e erros visuais.",
        files = listOf(
            ModelFile(
                "https://huggingface.co/ggml-org/moondream2-20250414-GGUF/resolve/main/moondream2-text-model-f16_ct-vicuna.gguf",
                "moondream2-text-model-f16_ct-vicuna.gguf", 2_839_535_072L
            ),
            ModelFile(
                "https://huggingface.co/ggml-org/moondream2-20250414-GGUF/resolve/main/moondream2-mmproj-f16-20250414.gguf",
                "moondream2-mmproj-f16-20250414.gguf", 909_777_984L
            )
        ),
        minRamMb = 5_000,
        runtime = "llama.cpp (mtmd)"
    )
}

enum class ModelState(val label: String) {
    NOT_INSTALLED("Modelo nao instalado"),
    DOWNLOADING("Baixando"),
    INSTALLING("Instalando"),
    INSTALLED("Instalado"),
    LOADING("Carregando"),
    READY("Pronto"),
    ERROR("Erro")
}

/** Resultado da verificacao automatica de compatibilidade. */
data class CompatReport(
    val isAndroid: Boolean,
    val is64Bit: Boolean,
    val sdkOk: Boolean,
    val totalRamMb: Long,
    val availRamMb: Long,
    val lowRamDevice: Boolean,
    val ramOk: Boolean,
    val freeStorageBytes: Long,
    val storageOk: Boolean,
    val runtimeAvailable: Boolean,
    val installed: Boolean
) {
    val canInstall get() = isAndroid && is64Bit && sdkOk && ramOk && (storageOk || installed)
    val canRun get() = canInstall && runtimeAvailable && installed

    fun lines(spec: ModelSpec): List<Pair<Boolean, String>> = listOf(
        isAndroid to "Aparelho Android (${Build.MANUFACTURER} ${Build.MODEL})",
        is64Bit to "Processador 64 bits (arm64-v8a)",
        sdkOk to "Android 8.0 ou superior (atual: API ${Build.VERSION.SDK_INT})",
        ramOk to "Memoria: ${totalRamMb} MB no total, ${availRamMb} MB livres (minimo ${spec.minRamMb} MB)",
        (storageOk || installed) to "Armazenamento livre: ${fmtBytes(freeStorageBytes)} (necessario ${fmtBytes(spec.totalBytes + SAFETY_MARGIN)})",
        runtimeAvailable to "Motor local ${spec.runtime} incluido no app"
    )

    companion object { const val SAFETY_MARGIN = 800L * 1024 * 1024 }
}

fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.2f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.0f MB".format(b / (1L shl 20).toDouble())
    else -> "$b B"
}

/**
 * Gerencia download, verificacao, armazenamento local e exclusao dos modelos.
 * Um modelo so e marcado como instalado depois que todos os arquivos foram
 * baixados por completo e o tamanho conferido.
 */
class ModelManager(private val ctx: Context) {

    private val baseDir = File(ctx.filesDir, "modelos").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true).followSslRedirects(true).build()
    @Volatile private var activeCall: Call? = null

    fun dir(spec: ModelSpec) = File(baseDir, spec.id)
    fun file(spec: ModelSpec, f: ModelFile) = File(dir(spec), f.fileName)
    private fun marker(spec: ModelSpec) = File(dir(spec), ".instalado")

    fun isInstalled(spec: ModelSpec): Boolean =
        marker(spec).exists() && spec.files.all { file(spec, it).length() == it.sizeBytes }

    fun downloadedBytes(spec: ModelSpec): Long = spec.files.sumOf { f ->
        val done = file(spec, f); val part = File(dir(spec), f.fileName + ".part")
        if (done.exists()) done.length() else part.length()
    }

    fun check(spec: ModelSpec, runtimeAvailable: Boolean): CompatReport {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mi.totalMem / (1024 * 1024)
        val free = StatFs(ctx.filesDir.absolutePath).availableBytes
        val remaining = (spec.totalBytes - downloadedBytes(spec)).coerceAtLeast(0)
        return CompatReport(
            isAndroid = true,
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a"),
            sdkOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O,
            totalRamMb = totalMb,
            availRamMb = mi.availMem / (1024 * 1024),
            lowRamDevice = am.isLowRamDevice,
            ramOk = totalMb >= spec.minRamMb && !am.isLowRamDevice,
            freeStorageBytes = free,
            storageOk = free >= remaining + CompatReport.SAFETY_MARGIN,
            runtimeAvailable = runtimeAvailable,
            installed = isInstalled(spec)
        )
    }

    /**
     * Baixa (com retomada) todos os arquivos do modelo.
     * [onProgress] recebe (fase, bytesFeitos, bytesTotais).
     */
    suspend fun install(spec: ModelSpec, onProgress: (ModelState, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        dir(spec).mkdirs()
        marker(spec).delete()
        val total = spec.totalBytes
        var before = 0L
        for (f in spec.files) {
            val dest = file(spec, f)
            if (dest.length() == f.sizeBytes) { before += f.sizeBytes; continue }
            val part = File(dir(spec), f.fileName + ".part")
            if (part.length() > f.sizeBytes) part.delete()
            var have = part.length()
            val req = Request.Builder().url(f.url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()
            val call = client.newCall(req); activeCall = call
            call.execute().use { resp ->
                if (resp.code == 200 && have > 0) { have = 0; part.delete() } // servidor ignorou Range
                if (!resp.isSuccessful) throw IllegalStateException("Falha no download (HTTP ${resp.code}). Verifique sua conexao.")
                val body = resp.body ?: throw IllegalStateException("Resposta vazia do servidor.")
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(have)
                    val buf = ByteArray(256 * 1024)
                    val input = body.byteStream()
                    var last = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf); if (n < 0) break
                        raf.write(buf, 0, n); have += n
                        val now = System.currentTimeMillis()
                        if (now - last > 250) { last = now; onProgress(ModelState.DOWNLOADING, before + have, total) }
                    }
                }
            }
            activeCall = null
            onProgress(ModelState.INSTALLING, before + have, total)
            if (part.length() != f.sizeBytes) throw IllegalStateException(
                "Arquivo ${f.fileName} incompleto (${fmtBytes(part.length())} de ${fmtBytes(f.sizeBytes)}). Toque em instalar para continuar de onde parou."
            )
            if (!part.renameTo(dest)) throw IllegalStateException("Nao foi possivel finalizar a instalacao de ${f.fileName}.")
            before += f.sizeBytes
        }
        onProgress(ModelState.INSTALLING, total, total)
        // Verificacao final de integridade (tamanho) e cabecalho GGUF
        for (f in spec.files) {
            val dest = file(spec, f)
            if (dest.length() != f.sizeBytes) throw IllegalStateException("Verificacao falhou para ${f.fileName}.")
            val magic = ByteArray(4); dest.inputStream().use { it.read(magic) }
            if (String(magic) != "GGUF") throw IllegalStateException("${f.fileName} nao e um arquivo GGUF valido.")
        }
        FileOutputStream(marker(spec)).use { it.write(System.currentTimeMillis().toString().toByteArray()) }
    }

    fun cancel() { activeCall?.cancel(); activeCall = null }

    fun delete(spec: ModelSpec) { cancel(); dir(spec).deleteRecursively() }

    companion object {
        fun isCancel(t: Throwable) = t is CancellationException || (t is java.io.IOException && t.message?.contains("Canceled", true) == true)
    }
}
