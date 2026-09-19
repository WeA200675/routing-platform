package org.routingplatform.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiReleaseAcceptanceGateTest {
    private val runtime = OpenSourceComponentMetadata(
        "runtime", "1".repeat(40), "MIT", "https://example.invalid/runtime"
    )
    private val model = LocalModelArtifactMetadata(
        "model", "model-revision-123", "Apache-2.0", "a".repeat(64),
        "https://example.invalid/model"
    )
    private val provenance = SocialAiArtifactProvenance(runtime, model, "GGUF", "Q4_K_M", 1, 1)
    private val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
    private val buildEvidence = SocialAiRuntimeBuildEvidence(
        "b".repeat(64), mapOf("arm64-v8a" to "c".repeat(64)),
        mapOf("ndk" to "28.2.13676358"), listOf("-DGGML_OPENMP=OFF"), "d".repeat(64),
        sbomComponents = setOf("runtime", "model"),
    )
    private val manifest = SocialAiRuntimeReleaseManifest(
        "runtime", "1".repeat(40), "model", "model-revision-123", "a".repeat(64),
        mapOf("arm64-v8a" to "c".repeat(64)), "d".repeat(64),
    )
    private val packaged = SocialAiPackagedNativeArtifacts(
        mapOf("arm64-v8a" to "c".repeat(64))
    )

    @Test
    fun softwareEvidenceAloneNeverMarksMergeReady() {
        val result = SocialAiReleaseAcceptanceGate.evaluate(
            attestation, buildEvidence, manifest, packaged, null, "arm64-v8a"
        )
        assertTrue(result.softwareReady)
        assertFalse(result.mergeReady)
    }

    @Test
    fun passingPhysicalEvidenceCompletesAcceptance() {
        val result = SocialAiReleaseAcceptanceGate.evaluate(
            attestation, buildEvidence, manifest, packaged, physicalEvidence(), "arm64-v8a"
        )
        assertTrue(result.mergeReady)
    }

    private fun physicalEvidence() = SocialAiPhysicalDeviceGateEvidence(
        SocialAiDevicePerformanceEvidence(20, 500, 1_000, 1_500, 1_100, (1L..20L).toList()),
        SocialAiDeviceSafetyEvidence(20, 0, 10, 0, 10, 0, 5, 0),
        "c".repeat(64), "a".repeat(64),
    )
}
