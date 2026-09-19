package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiRuntimeAttestationTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "runtime-commit-123", "MIT", "https://example.invalid/runtime"
    )
    private val model = LocalModelArtifactMetadata(
        "model", "model-revision-123", "Apache-2.0", "a".repeat(64),
        "https://example.invalid/model"
    )
    private val provenance = SocialAiArtifactProvenance(
        runtime, model, "GGUF", "Q4_K_M", 1024, 2048
    )

    @Test
    fun admittedAttestationMatchesBackendAndAbi() {
        SocialAiRuntimeAttestationGate.requireAdmitted(
            SocialAiRuntimeAttestation(provenance, "build-123", setOf("arm64-v8a")),
            backend(runtime, model),
            "arm64-v8a",
        )
    }

    @Test
    fun wrongModelMetadataFailsClosed() {
        val different = model.copy(sha256 = "b".repeat(64))
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeAttestationGate.requireAdmitted(
                SocialAiRuntimeAttestation(provenance, "build-123", setOf("arm64-v8a")),
                backend(runtime, different),
                "arm64-v8a",
            )
        }
    }

    @Test
    fun unattestedAbiFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeAttestationGate.requireAdmitted(
                SocialAiRuntimeAttestation(provenance, "build-123", setOf("arm64-v8a")),
                backend(runtime, model),
                "x86_64",
            )
        }
    }

    @Test
    fun floatingRevisionRemainsRejected() {
        val floating = provenance.copy(runtime = runtime.copy(revision = "main"))
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeAttestationGate.requireAdmitted(
                SocialAiRuntimeAttestation(floating, "build-123", setOf("arm64-v8a")),
                backend(floating.runtime, model),
                "arm64-v8a",
            )
        }
    }

    private fun backend(
        runtimeMetadata: OpenSourceComponentMetadata,
        modelMetadata: LocalModelArtifactMetadata,
    ) = object : LocalSocialAiTextGenerationBackend, SocialAiRuntimeBuildIdentified {
        override val backendId = "local"
        override val runtimeBuildId = "build-123"
        override val runtimeMetadata = runtimeMetadata
        override val modelMetadata = modelMetadata
        override fun generate(request: SocialAiTextGenerationRequest) =
            SocialAiTextGenerationResult("local", backendId, true)
    }
}
