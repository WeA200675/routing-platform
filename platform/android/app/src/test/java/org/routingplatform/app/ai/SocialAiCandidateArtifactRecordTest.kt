package org.routingplatform.app.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiCandidateArtifactRecordTest {
    private val candidate = SocialAiRuntimeCandidateCatalog.smolLm2_360mInstructQ4Km
    private val record = SocialAiCandidateArtifactRecord(
        "1".repeat(40), "model-revision-123", "0123456789abcdef".repeat(4), 271_000_000
    )

    @Test
    fun exactDownloadedArtifactIsAccepted() {
        SocialAiCandidateArtifactRecordGate.requireMatchesCandidate(
            record, candidate, candidate.ggufFilename, record.modelSha256, record.modelBytes
        )
    }

    @Test
    fun substitutedGgufIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiCandidateArtifactRecordGate.requireMatchesCandidate(
                record, candidate, candidate.ggufFilename, "f".repeat(64), record.modelBytes
            )
        }
    }
}
