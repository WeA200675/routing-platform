package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiReleaseCandidateGateTest {
    @Test
    fun fixtureSourcesAndRepeatedHashesAreRejected() {
        val runtime = OpenSourceComponentMetadata(
            "runtime", "1".repeat(40), "MIT", "https://example.invalid/runtime"
        )
        val model = LocalModelArtifactMetadata(
            "model", "revision-123", "MIT", "a".repeat(64), "https://example.invalid/model"
        )
        val provenance = SocialAiArtifactProvenance(runtime, model, "GGUF", "Q4", 1, 1)
        val attestation = SocialAiRuntimeAttestation(provenance, "build-1", setOf("arm64-v8a"))
        val evidence = SocialAiRuntimeBuildEvidence(
            "b".repeat(64), mapOf("arm64-v8a" to "c".repeat(64)),
            mapOf("ndk" to "28.2.13676358"), listOf("-DTEST=OFF"), "d".repeat(64),
            sbomComponents = setOf("runtime", "model"),
        )
        val manifest = SocialAiRuntimeReleaseManifest(
            "runtime", "1".repeat(40), "model", "revision-123", "a".repeat(64),
            mapOf("arm64-v8a" to "c".repeat(64)), "d".repeat(64),
        )
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiReleaseCandidateGate.requireRealCandidate(attestation, evidence, manifest)
        }
    }
}
