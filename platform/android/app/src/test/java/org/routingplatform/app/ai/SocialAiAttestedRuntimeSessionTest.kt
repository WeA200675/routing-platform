package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiAttestedRuntimeSessionTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "1".repeat(40), "MIT", "https://example.invalid/runtime"
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

    @Test
    fun mismatchedNativeBuildFailsBeforeNativeLoad() {
        val backend = FakeBackend(runtimeBuildId = "unexpected-build")
        val session = session(backend, setOf("arm64-v8a"), "arm64-v8a")
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    @Test
    fun missingBuildEvidenceFailsBeforeNativeLoad() {
        val backend = FakeBackend()
        val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
        val session = SocialAiAttestedRuntimeSession(
            backend,
            SocialAiModelLifecycle(backend, SocialAiRuntimeLimits(minimumAvailableMemoryBytes = 0)),
            attestation,
            "arm64-v8a",
        )
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    @Test
    fun missingReleaseManifestFailsBeforeNativeLoad() {
        val backend = FakeBackend()
        val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
        val session = SocialAiAttestedRuntimeSession(
            backend,
            SocialAiModelLifecycle(backend, SocialAiRuntimeLimits(minimumAvailableMemoryBytes = 0)),
            attestation,
            "arm64-v8a",
            evidence(setOf("arm64-v8a")),
        )
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    @Test
    fun mismatchedNativeArtifactHashFailsBeforeNativeLoad() {
        val backend = FakeBackend(runtimeArtifactSha256 = "e".repeat(64))
        val session = session(backend, setOf("arm64-v8a"), "arm64-v8a")
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
        assertTrue(result is SocialAiModelLoadResult.Rejected)
        assertFalse(backend.loadCalled)
        file.delete()
    }

    @Test
    fun backendWithoutBuildIdentityFailsBeforeNativeLoad() {
        val backend = UnidentifiedBackend()
        val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
        val session = SocialAiAttestedRuntimeSession(
            backend,
            SocialAiModelLifecycle(backend, SocialAiRuntimeLimits(minimumAvailableMemoryBytes = 0)),
            attestation,
            "arm64-v8a",
            evidence(setOf("arm64-v8a")),
            manifest(setOf("arm64-v8a")),
        )
        val file = File.createTempFile("model", ".gguf")
        val result = session.load(SocialAiModelLoadRequest(file, model, Long.MAX_VALUE))
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
            evidence(abis),
            manifest(abis),
        )
    }

    private fun evidence(abis: Set<String>) = SocialAiRuntimeBuildEvidence(
        sourceSha256 = "b".repeat(64),
        artifactSha256ByAbi = abis.associateWith { "c".repeat(64) },
        toolchain = mapOf("ndk" to "28.2.13676358", "cmake" to "3.22.1"),
        buildArguments = listOf("-DGGML_OPENMP=OFF", "-DCMAKE_BUILD_TYPE=Release"),
        sbomSha256 = "d".repeat(64),
        sbomComponents = setOf("runtime", "model"),
    )

    private fun manifest(abis: Set<String>) = SocialAiRuntimeReleaseManifest(
        runtimeComponentId = runtime.componentId,
        runtimeRevision = runtime.revision,
        modelId = model.modelId,
        modelRevision = model.revision,
        modelSha256 = model.sha256,
        runtimeArtifactSha256ByAbi = abis.associateWith { "c".repeat(64) },
        sbomSha256 = "d".repeat(64),
    )

    private inner class FakeBackend(
        override val runtimeBuildId: String = "build-1",
        override val runtimeArtifactSha256: String = "c".repeat(64),
    ) : ManagedLocalSocialAiBackend, SocialAiRuntimeBuildIdentified, SocialAiRuntimeArtifactIdentified {
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

    private inner class UnidentifiedBackend : ManagedLocalSocialAiBackend {
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
