package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiRuntimeBuildEvidenceTest {
    private val provenance = SocialAiArtifactProvenance(
        OpenSourceComponentMetadata("runtime", "1".repeat(40), "MIT", "https://example.invalid/runtime"),
        LocalModelArtifactMetadata("model", "revision-123", "Apache-2.0", "a".repeat(64), "https://example.invalid/model"),
        "GGUF", "Q4_K_M", 1024, 2048,
    )
    private val attestation = SocialAiRuntimeAttestation(provenance, "build-123", setOf("arm64-v8a"))

    @Test
    fun completeEvidenceIsAdmitted() {
        SocialAiRuntimeDistributionGate.requireAdmitted(attestation, evidence())
    }

    @Test
    fun missingAttestedAbiHashFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeDistributionGate.requireAdmitted(
                attestation.copy(supportedAbis = setOf("arm64-v8a", "x86_64")),
                evidence(),
            )
        }
    }

    @Test
    fun floatingToolchainVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            evidence(toolchain = mapOf("ndk" to "latest"))
        }
    }

    @Test
    fun missingModelSbomCoverageFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiRuntimeDistributionGate.requireAdmitted(
                attestation,
                evidence().copy(sbomComponents = setOf("runtime")),
            )
        }
    }

    @Test
    fun unsupportedSbomSchemaIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            evidence().copy(sbomFormat = "unreviewed")
        }
    }

    @Test
    fun malformedSbomDigestIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            evidence(sbomSha256 = "unchecked")
        }
    }

    private fun evidence(
        toolchain: Map<String, String> = mapOf("ndk" to "28.2.13676358", "cmake" to "3.22.1"),
        sbomSha256: String = "c".repeat(64),
    ) = SocialAiRuntimeBuildEvidence(
        sourceSha256 = "b".repeat(64),
        artifactSha256ByAbi = mapOf("arm64-v8a" to "d".repeat(64)),
        toolchain = toolchain,
        buildArguments = listOf("-DGGML_OPENMP=OFF", "-DCMAKE_BUILD_TYPE=Release"),
        sbomSha256 = sbomSha256,
        sbomComponents = setOf("runtime", "model"),
    )
}
