package org.routingplatform.app.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiPhysicalDeviceGateTest {
    private val manifest = SocialAiRuntimeReleaseManifest(
        "runtime", "1".repeat(40), "model", "model-revision-123", "a".repeat(64),
        mapOf("arm64-v8a" to "c".repeat(64)), "d".repeat(64),
    )

    @Test
    fun exactArtifactIdentityAndPassingEvidenceAreAccepted() {
        assertTrue(SocialAiPhysicalDeviceGate.validate(evidence(), manifest, "arm64-v8a").isEmpty())
    }

    @Test
    fun deviceArtifactSubstitutionFailsClosed() {
        assertTrue(
            SocialAiPhysicalDeviceGate.validate(
                evidence().copy(runtimeArtifactSha256 = "e".repeat(64)),
                manifest, "arm64-v8a",
            ).isNotEmpty()
        )
    }

    private fun evidence() = SocialAiPhysicalDeviceGateEvidence(
        performance = SocialAiDevicePerformanceEvidence(
            20, 500, 1_000, 1_500, 1_100, (1L..20L).toList()
        ),
        safety = SocialAiDeviceSafetyEvidence(20, 0, 10, 0, 10, 0, 5, 0),
        runtimeArtifactSha256 = "c".repeat(64),
        modelSha256 = "a".repeat(64),
    )
}
