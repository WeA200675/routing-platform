package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiRuntimeSessionTest {
    @Test
    fun generationFailureUnloadsBackendAndMarksSessionFailed() {
        val file = File.createTempFile("g620", ".gguf")
        file.writeText("model")
        val digest = SocialAiModelIntegrity.sha256(file)
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", digest, "https://example.invalid/model"
        )
        val backend = FailingBackend(metadata)
        val session = SocialAiRuntimeSession(backend, SocialAiModelLifecycle(backend))
        val load = session.load(SocialAiModelLoadRequest(file, metadata, Long.MAX_VALUE))
        assertTrue(load is SocialAiModelLoadResult.Loaded)
        assertThrows(IllegalStateException::class.java) { session.generate(request()) }
        assertTrue(session.state is SocialAiRuntimeState.Failed)
        assertFalse(backend.isLoaded)
        file.delete()
    }

    @Test
    fun generationBeforeLoadIsRejected() {
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", "a".repeat(64), "https://example.invalid/model"
        )
        val backend = FailingBackend(metadata)
        val session = SocialAiRuntimeSession(backend, SocialAiModelLifecycle(backend))
        assertThrows(IllegalStateException::class.java) { session.generate(request()) }
    }

    private fun request() = SocialAiTextGenerationRequest(
        systemInstruction = "policy",
        userText = "hello",
        responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
    )

    private class FailingBackend(
        override val modelMetadata: LocalModelArtifactMetadata,
    ) : ManagedLocalSocialAiBackend {
        override val backendId = "failing-local"
        override val runtimeMetadata = OpenSourceComponentMetadata(
            "runtime", "immutable", "MIT", "https://example.invalid/runtime"
        )
        override var isLoaded = false
        override fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
            isLoaded = true
            return SocialAiModelLoadResult.Loaded(modelMetadata.modelId)
        }
        override fun unload() { isLoaded = false }
        override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
            throw IllegalStateException("native failure")
        }
    }
}
