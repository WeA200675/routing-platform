package org.routingplatform.app.ai

/** Immutable inputs captured by the release-preparation job, never guessed by app code. */
data class SocialAiCandidateArtifactRecord(
    val runtimeCommitSha: String,
    val modelRevision: String,
    val modelSha256: String,
    val modelBytes: Long,
) {
    init {
        require(runtimeCommitSha.matches(Regex("[A-Fa-f0-9]{40}")))
        require(modelRevision.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{6,127}")))
        require(modelSha256.matches(Regex("[A-Fa-f0-9]{64}")))
        require(modelBytes > 0)
    }
}

object SocialAiCandidateArtifactRecordGate {
    fun requireMatchesCandidate(
        record: SocialAiCandidateArtifactRecord,
        candidate: SocialAiRuntimeCandidate,
        downloadedFilename: String,
        downloadedSha256: String,
        downloadedBytes: Long,
    ) {
        require(downloadedFilename == candidate.ggufFilename) {
            "Downloaded GGUF filename does not match the selected candidate."
        }
        require(downloadedSha256.equals(record.modelSha256, ignoreCase = true)) {
            "Downloaded GGUF SHA-256 does not match the immutable artifact record."
        }
        require(downloadedBytes == record.modelBytes) {
            "Downloaded GGUF size does not match the immutable artifact record."
        }
    }
}
