package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiRuntimeResourceSessionTest {
    @Test
    fun resourceRejectionDoesNotInvokeNativeBackendOrDestroyReadyModel() {
        var generateCalls = 0
        val file = File.createTempFile("g620", ".gguf").apply { writeText("model") }
        val digest = SocialAiModelIntegrity.sha256(file)
        val metadata = LocalModelArtifactMetadata(
            "test/model", "immutable", "MIT", digest, "https://example.invalid/model"
        )
        val backend = object : ManagedLocalSocialAiBackend {
            override val backendId = "local"
            override val runtimeMetadata = OpenSourceComponentMetadata(
                "runtime", "immutable", "MIT", "https://example.invalid/runtime"
            )
            override val modelMetadata = metadata
            override var isLoaded = false
            override fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
                isLoaded = true
                return SocialAiModelLoadResult.Loaded(metadata.modelId)
            }
            override fun unload() { isLoaded = false }
            override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
                generateCalls++
                return SocialAiTextGenerationResult("local", backendId, true)
            }
        }
        val lifecycle = SocialAiModelLifecycle(
            backend,
            SocialAiRuntimeLimits(minimumAvailableMemoryBytes = 0),
        )
        val session = SocialAiRuntimeSession(backend, lifecycle)
        session.load(SocialAiModelLoadRequest(file, metadata, Long.MAX_VALUE))

        assertThrows(IllegalStateException::class.java) {
            session.generate(
                request(),
                SocialAiRuntimeResources(availableMemoryBytes = 1),
            )
        }
        assertEquals(0, generateCalls)
        assertFalse(session.state is SocialAiRuntimeState.Failed)
        file.delete()
    }

    private fun request() = SocialAiTextGenerationRequest(
        systemInstruction = "Local only.",
        userText = "Hallo",
        responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
        maximumOutputTokens = 192,
    )
}
