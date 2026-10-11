package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedGgufLocalBackendTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "abc123", "MIT", "https://example.invalid/runtime"
    )
    private val model = LocalModelArtifactMetadata(
        "model", "def456", "Apache-2.0", "a".repeat(64), "https://example.invalid/model"
    )

    @Test
    fun generationRequiresExplicitLoad() {
        val engine = FakeEngine()
        val backend = VerifiedGgufLocalBackend("gguf-local", runtime, model, engine)
        assertThrows(IllegalStateException::class.java) {
            backend.generate(request())
        }
        assertFalse(engine.generated)
    }

    @Test
    fun backendHasNoCloudFallbackAndReportsLocalResult() {
        val engine = FakeEngine()
        val backend = VerifiedGgufLocalBackend("gguf-local", runtime, model, engine)
        val file = File.createTempFile("model", ".gguf")
        backend.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        val result = backend.generate(request())
        assertTrue(result.localInference)
        assertEquals("gguf-local", result.backendId)
        assertEquals("local answer", result.text)
        backend.unload()
        assertFalse(backend.isLoaded)
        file.delete()
    }

    @Test
    fun floatingRevisionIsRejectedByAdmissionGate() {
        val provenance = SocialAiArtifactProvenance(
            runtime.copy(revision = "main"), model, "GGUF", "Q4_K_M", 1, 1
        )
        assertTrue(SocialAiOpenSourceAdmission.validate(provenance).isNotEmpty())
    }

    private fun request() = SocialAiTextGenerationRequest(
        systemInstruction = "Local only.",
        userText = "Hallo",
        responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
    )

    private class FakeEngine : SocialAiNativeEngine {
        override val engineId = "fake"
        override val artifactSha256 = "f".repeat(64)
        var generated = false
        override fun loadModel(localPath: String, contextTokens: Int) = true
        override fun unloadModel() = Unit
        override fun generate(prompt: String, maximumOutputTokens: Int): String {
            generated = true
            return "local answer"
        }
    }
}
