package org.routingplatform.app.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JniSocialAiNativeEngineContractTest {
    @Test
    fun jniAdapterHasNoNetworkSurfaceAndUsesReviewedLibraryName() {
        val source = File("src/main/java/org/routingplatform/app/ai/JniSocialAiNativeEngine.kt").readText()
        assertTrue(source.contains("social_ai_llama"))
        assertTrue(source.contains("System.loadLibrary"))
        assertTrue(!source.contains("http://"))
        assertTrue(!source.contains("https://"))
        assertTrue(!source.contains("java.net"))
        assertTrue(!source.contains("okhttp", ignoreCase = true))
    }
}
