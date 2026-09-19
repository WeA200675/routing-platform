package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiNativeBuildCaptureTest {
    private val record = SocialAiCandidateArtifactRecord(
        "1".repeat(40), "model-revision-123", "0123456789abcdef".repeat(4), 271_000_000
    )

    private fun capture(
        ndk: String = "28.2.13676358",
        filename: String = "libsocial_ai_llama.so",
        bytes: Long = 123_456,
    ) = SocialAiNativeBuildCapture(
        "1".repeat(40), ndk, "3.22.1", "arm64-v8a", filename, "2".repeat(64), bytes
    )

    @Test fun exactToolchainCommitAndLibraryIdentityAreAccepted() {
        SocialAiNativeBuildCaptureGate.requireMatches(
            capture(), record, "28.2.13676358", "3.22.1"
        )
    }

    @Test fun changedToolchainIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiNativeBuildCaptureGate.requireMatches(
                capture(ndk = "29.0.14206865"), record, "28.2.13676358", "3.22.1"
            )
        }
    }

    @Test fun substitutedNativeLibraryIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            capture(filename = "libsubstituted.so")
        }
    }

    @Test fun emptyNativeArtifactIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            capture(bytes = 0)
        }
    }
}
