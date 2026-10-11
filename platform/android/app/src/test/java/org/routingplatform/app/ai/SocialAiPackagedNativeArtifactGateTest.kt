package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiPackagedNativeArtifactGateTest {
    private val manifest = SocialAiRuntimeReleaseManifest(
        "runtime", "1".repeat(40), "model", "model-revision-123", "a".repeat(64),
        mapOf("arm64-v8a" to "c".repeat(64)), "d".repeat(64),
    )

    @Test
    fun independentlyHashedPackagedArtifactIsAdmitted() {
        SocialAiPackagedNativeArtifactGate.requireAdmitted(
            SocialAiPackagedNativeArtifacts(mapOf("arm64-v8a" to "c".repeat(64))),
            manifest,
        )
    }

    @Test
    fun extraOrMissingAbiFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiPackagedNativeArtifactGate.requireAdmitted(
                SocialAiPackagedNativeArtifacts(
                    mapOf("arm64-v8a" to "c".repeat(64), "x86_64" to "e".repeat(64))
                ),
                manifest,
            )
        }
    }

    @Test
    fun packagedBinarySubstitutionFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiPackagedNativeArtifactGate.requireAdmitted(
                SocialAiPackagedNativeArtifacts(mapOf("arm64-v8a" to "e".repeat(64))),
                manifest,
            )
        }
    }
}
