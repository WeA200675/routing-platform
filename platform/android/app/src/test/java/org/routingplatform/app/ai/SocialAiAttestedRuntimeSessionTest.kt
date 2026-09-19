package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiAttestedRuntimeSessionTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "abc123", "MIT", "https://example.invalid/runtime"
    )
    private val model = LocalModelArtifactMetadata(
        "model", "def456", "Apache-2.0", "a".repeat(64), "https://example.invalid/model"
    )
    private val provenance = SocialAiArtifactProvenance(
        runtime, model, "GGUF", "Q4_K_M", 1, 1
    )

    @Test
    fun unsupportedAbiFailsBeforeNativeLoad() {
        val backend = FakeBackend()
        val session = session(backend, setOf("arm64-v8a"), "x86_64")
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    @Test
    fun mismatchedLoadMetadataFailsBeforeNativeLoad() {
        val backend = FakeBackend()
        val session = session(backend, setOf("arm64-v8a"), "arm64-v8a")
        val file = File.createTempFile("model", ".gguf")
        val other = model.copy(modelId = "other")
        val result = session.load(SocialAiModelLoadRequest(file, other, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    private fun session(
        backend: FakeBackend,
        abis: Set<String>,
        deviceAbi: String,
    ): SocialAiAttestedRuntimeSession {
        val attestation = SocialAiRuntimeAttestation(provenance, "build-1", abis)
        return SocialAiAttestedRuntimeSession(
            backend,
            SocialAiModelLifecycle(backend, SocialAiRuntimeLimits(minimumAvailableMemoryBytes = 0)),
            attestation,
            deviceAbi,
        )
    }

    private inner class FakeBackend : ManagedLocalSocialAiBackend {
        override val backendId = "local"
        override val runtimeMetadata = runtime
        override val modelMetadata = model
        override var isLoaded = false
        var loadCalled = false

        override fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
            loadCalled = true
            isLoaded = true
            return SocialAiModelLoadResult.Loaded(model.modelId)
        }

        override fun unload() { isLoaded = false }

        override fun generate(request: SocialAiTextGenerationRequest) =
            SocialAiTextGenerationResult("local", backendId, true)
    }
}
