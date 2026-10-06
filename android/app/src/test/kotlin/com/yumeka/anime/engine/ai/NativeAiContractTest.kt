package com.yumeka.anime.engine.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes JVM do contrato Kotlin <-> C++ (nao executam os motores). */
class NativeAiContractTest {

    @Test fun errorCodesMatchNativeHeader() {
        // Mesmos valores de android/native-ai/common/yumeka_jni_common.h
        assertEquals(NativeAiException.Kind.RUNTIME, NativeAiException(1, "x").kind)
        assertEquals(NativeAiException.Kind.MODEL, NativeAiException(2, "x").kind)
        assertEquals(NativeAiException.Kind.OUT_OF_MEMORY, NativeAiException(3, "x").kind)
        assertEquals(NativeAiException.Kind.CANCELLED, NativeAiException(4, "x").kind)
        assertEquals(NativeAiException.Kind.INVALID_ARG, NativeAiException(5, "x").kind)
        assertEquals(NativeAiException.Kind.RUNTIME, NativeAiException(99, "x").kind)
    }

    @Test fun unavailableMessageKeepsRealReason() {
        val m = unavailableMessage("dlopen failed: library \"libyumeka_sd.so\" not found")
        assertTrue(m.startsWith(UNSUPPORTED_MESSAGE))
        assertTrue(m.contains("libyumeka_sd.so"))
        assertEquals(UNSUPPORTED_MESSAGE, unavailableMessage(null))
    }

    @Test fun analysisUsesInternalPrompt() {
        val qs = analysisQuestions("um gato samurai")
        assertEquals(5, qs.size)
        assertTrue(qs.last().contains(ANALYSIS_PROMPT))
        assertTrue(qs.last().contains("um gato samurai"))
    }

    @Test fun safeDefaultsFitDevice() {
        // 256 e 384 precisam ser multiplos de 64 (validado tambem no C++).
        listOf(256, 384).forEach { assertEquals(0, it % 64) }
    }
}
