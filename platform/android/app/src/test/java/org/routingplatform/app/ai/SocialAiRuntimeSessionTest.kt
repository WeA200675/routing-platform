package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiRuntimeSessionTest {
    @Test
    fun generationFailureUnloadsBackendAndRevokesLease() {
        val fixture = fixture(failGeneration = true)
        assertTrue(fixture.session.load(fixture.request) is SocialAiModelLoadResult.Loaded)
        assertNotNull(fixture.session.modelLease)
        assertThrows(IllegalStateException::class.java) { fixture.session.generate(request()) }
        assertTrue(fixture.session.state is SocialAiRuntimeState.Failed)
        assertFalse(fixture.backend.isLoaded)
        assertNull(fixture.session.modelLease)
        fixture.file.delete()
    }

    @Test
    fun explicitUnloadRevokesVerifiedLease() {
        val fixture = fixture(failGeneration = false)
        assertTrue(fixture.session.load(fixture.request) is SocialAiModelLoadResult.Loaded)
        assertNotNull(fixture.session.modelLease)
        fixture.session.unload()
        assertNull(fixture.session.modelLease)
        assertFalse(fixture.backend.isLoaded)
        assertTrue(fixture.session.state is SocialAiRuntimeState.Unloaded)
        fixture.file.delete()
    }

    @Test
    fun generationBeforeLoadIsRejected() {
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", "a".repeat(64), "https://example.invalid/model"
        )
        val backend = TestBackend(metadata, failGeneration = true)
        val session = SocialAiRuntimeSession(backend, SocialAiModelLifecycle(backend))
        assertThrows(IllegalStateException::class.java) { session.generate(request()) }
    }

    private fun fixture(failGeneration: Boolean): Fixture {
        val file = File.createTempFile("g620", ".gguf")
        file.writeText("model")
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", SocialAiModelIntegrity.sha256(file),
            "https://example.invalid/model"
        )
        val backend = TestBackend(metadata, failGeneration)
        return Fixture(
            file, backend, SocialAiRuntimeSession(backend, SocialAiModelLifecycle(backend)),
            SocialAiModelLoadRequest(file, metadata, Long.MAX_VALUE)
        )
    }

    private fun request() = SocialAiTextGenerationRequest(
        systemInstruction = "policy",
        userText = "hello",
        responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
    )

    private data class Fixture(
        val file: File,
        val backend: TestBackend,
        val session: SocialAiRuntimeSession,
        val request: SocialAiModelLoadRequest,
    )

    private class TestBackend(
        override val modelMetadata: LocalModelArtifactMetadata,
        private val failGeneration: Boolean,
    ) : ManagedLocalSocialAiBackend {
        override val backendId = "test-local"
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
            if (failGeneration) throw IllegalStateException("native failure")
            return SocialAiTextGenerationResult("local result", backendId, localInference = true)
        }
    }
}
