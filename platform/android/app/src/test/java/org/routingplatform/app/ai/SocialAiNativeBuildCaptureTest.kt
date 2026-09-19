package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiNativeBuildCaptureTest {
    private val record=SocialAiCandidateArtifactRecord(
        "1".repeat(40),"model-revision-123","0123456789abcdef".repeat(4),271_000_000
    )

    @Test fun exactToolchainAndCommitAreAccepted() {
        SocialAiNativeBuildCaptureGate.requireMatches(
            SocialAiNativeBuildCapture("1".repeat(40),"28.2.13676358","3.22.1","arm64-v8a","2".repeat(64)),
            record,"28.2.13676358","3.22.1"
        )
    }

    @Test fun changedToolchainIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiNativeBuildCaptureGate.requireMatches(
                SocialAiNativeBuildCapture("1".repeat(40),"29.0.14206865","3.22.1","arm64-v8a","2".repeat(64)),
                record,"28.2.13676358","3.22.1"
            )
        }
    }
}
