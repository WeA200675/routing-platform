package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiRuntimeReleaseManifestTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "1".repeat(40), "MIT", "https://example.invalid/runtime"
    )
    private val model = LocalModelArtifactMetadata(
        "model", "model-revision-123", "Apache-2.0", "a".repeat(64),
        "https://example.invalid/model"
    )
    private val provenance = SocialAiArtifactProvenance(runtime, model, "GGUF", "Q4_K_M", 1, 1)
    private val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
    private val evidence = SocialAiRuntimeBuildEvidence(
        "b".repeat(64), mapOf("arm64-v8a" to "c".repeat(64)),
        mapOf("ndk" to "28.2.13676358"), listOf("-DGGML_OPENMP=OFF"), "d".repeat(64),
        sbomComponents = setOf("runtime", "model"),
    )

    @Test
    fun exactReviewedReleaseManifestIsAdmitted() {
        SocialAiRuntimeReleaseGate.requireAdmitted(manifest(), attestation, evidence)
    }

    @Test
    fun substitutedNativeArtifactFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeReleaseGate.requireAdmitted(
                manifest().copy(runtimeArtifactSha256ByAbi = mapOf("arm64-v8a" to "e".repeat(64))),
                attestation, evidence,
            )
        }
    }

    @Test
    fun substitutedModelHashFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeReleaseGate.requireAdmitted(
                manifest().copy(modelSha256 = "e".repeat(64)), attestation, evidence,
            )
        }
    }

    private fun manifest() = SocialAiRuntimeReleaseManifest(
        "runtime", "1".repeat(40), "model", "model-revision-123", "a".repeat(64),
        mapOf("arm64-v8a" to "c".repeat(64)), "d".repeat(64),
    )
}
