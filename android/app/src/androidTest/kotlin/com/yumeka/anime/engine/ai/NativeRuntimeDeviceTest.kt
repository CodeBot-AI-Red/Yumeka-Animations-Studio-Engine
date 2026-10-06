package com.yumeka.anime.engine.ai

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.concurrent.thread

/**
 * Testes REAIS no aparelho (ex.: Moto Edge 30 Neo). Rode com:
 *   ./gradlew :app:connectedDebugAndroidTest
 * Os testes de modelo sao pulados (assume) se o modelo ainda nao foi instalado pelo app.
 */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val mm = ModelManager(ctx)

    @After fun tearDown() { AiSession.releaseAll(); assertFalse(AiSession.anyLoaded) }

    // --- inicializacao do runtime
    @Test fun sdRuntimeInitializes() {
        assertTrue("libyumeka_sd.so nao carregou: ${NativeDiffusion.loadError}", NativeDiffusion.available)
        assertTrue(NativeDiffusion.nativeSystemInfo().contains("stable-diffusion.cpp"))
    }

    @Test fun vlmRuntimeInitializes() {
        assertTrue("libyumeka_vlm.so nao carregou: ${NativeVision.loadError}", NativeVision.available)
        assertTrue(NativeVision.nativeSystemInfo().contains("llama.cpp"))
    }

    // --- erro de modelo
    @Test fun missingModelGivesModelError() {
        assumeTrue(NativeDiffusion.available)
        expectKind(NativeAiException.Kind.MODEL) { NativeDiffusion.nativeLoad("/nao/existe.gguf", 2, false) }
    }

    @Test fun corruptModelGivesModelError() {
        assumeTrue(NativeDiffusion.available)
        val f = File(ctx.cacheDir, "corrompido.gguf").apply { writeBytes("GGUF-lixo".toByteArray()) }
        expectKind(NativeAiException.Kind.MODEL) { NativeDiffusion.nativeLoad(f.absolutePath, 2, false) }
        f.delete()
    }

    @Test fun missingMoondreamGivesModelError() {
        assumeTrue(NativeVision.available)
        expectKind(NativeAiException.Kind.MODEL) { NativeVision.nativeLoad("/nao/existe.gguf", "/nao/mmproj.gguf", 2) }
    }

    // --- erro de runtime (handle invalido / parametros)
    @Test fun generateWithoutLoadGivesError() {
        assumeTrue(NativeDiffusion.available)
        expectKind(NativeAiException.Kind.INVALID_ARG) { NativeDiffusion.nativeGenerate(0, "x", "", 256, 256, 2, 1f, 1, null) }
    }

    // --- carregamento + geracao + descarregamento
    @Test fun loadGenerateUnload() {
        assumeTrue(NativeDiffusion.available && mm.isInstalled(Models.DREAMSHAPER))
        val p = GenParams("a red apple on a wooden table", "", "", 256, 256, 2, 1f, 42)
        val bmp = AiSession.generate(mm, p, {}, { _, _ -> })
        assertEquals(256, bmp.width); assertEquals(256, bmp.height)
        assertTrue("imagem uniforme: geracao suspeita", distinctColors(bmp) > 16)
        AiSession.unloadDiffusion()
        assertFalse(AiSession.anyLoaded)
    }

    @Test fun invalidResolutionRejected() {
        assumeTrue(NativeDiffusion.available && mm.isInstalled(Models.DREAMSHAPER))
        AiSession.generate(mm, GenParams("x", "", "", 256, 256, 1, 1f, 1), {}, { _, _ -> })
        expectKind(NativeAiException.Kind.INVALID_ARG) { AiSession.generate(mm, GenParams("x", "", "", 250, 256, 2, 1f, 1), {}, { _, _ -> }) }
    }

    // --- cancelamento
    @Test fun cancelGeneration() {
        assumeTrue(NativeDiffusion.available && mm.isInstalled(Models.DREAMSHAPER))
        var err: Throwable? = null
        val t = thread {
            try { AiSession.generate(mm, GenParams("a castle", "", "", 384, 384, 8, 1f, 7), {}, { _, _ -> }) } catch (e: Throwable) { err = e }
        }
        Thread.sleep(4000); AiSession.cancelAll(); t.join(180_000)
        assertTrue("esperado cancelamento, veio $err", (err as? NativeAiException)?.kind == NativeAiException.Kind.CANCELLED)
    }

    // --- Moondream2: carregamento + analise (e garante 1 modelo por vez)
    @Test fun moondreamAnalyzes() {
        assumeTrue(NativeVision.available && mm.isInstalled(Models.MOONDREAM2))
        val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 256) for (x in 0 until 256) setPixel(x, y, if (x < 128) Color.RED else Color.BLUE)
        }
        val ans = AiSession.ask(mm, bmp, listOf("What colors are in this image?"), {}, {})
        assertEquals(1, ans.size)
        assertTrue("resposta vazia", ans[0].isNotBlank())
        assertFalse("Moondream2 deveria ser descarregado apos a analise", AiSession.anyLoaded)
    }

    // --- memoria insuficiente: o erro precisa chegar tipado, nunca crashar
    @Test fun outOfMemoryIsReportedCleanly() {
        assumeTrue(NativeDiffusion.available && mm.isInstalled(Models.DREAMSHAPER))
        try {
            AiSession.generate(mm, GenParams("x", "", "", 1024, 1024, 1, 1f, 1), {}, { _, _ -> })
        } catch (e: NativeAiException) {
            assertTrue(e.kind == NativeAiException.Kind.OUT_OF_MEMORY || e.kind == NativeAiException.Kind.RUNTIME)
        }
        // Em qualquer caso o processo continua vivo e o modelo pode ser descarregado.
        AiSession.releaseAll()
    }

    private fun expectKind(kind: NativeAiException.Kind, block: () -> Unit) {
        try { block(); fail("esperado NativeAiException($kind)") } catch (e: NativeAiException) { assertEquals(e.message, kind, e.kind) }
    }

    private fun distinctColors(b: Bitmap): Int {
        val s = HashSet<Int>()
        for (y in 0 until b.height step 8) for (x in 0 until b.width step 8) s.add(b.getPixel(x, y))
        return s.size
    }
}
