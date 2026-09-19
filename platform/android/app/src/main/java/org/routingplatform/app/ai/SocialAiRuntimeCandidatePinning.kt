package org.routingplatform.app.ai

/**
 * Converts a reviewed candidate into production provenance only when callers
 * supply independently captured immutable revisions and the exact GGUF hash.
 */
object SocialAiRuntimeCandidatePinning {
    fun pin(
        candidate: SocialAiRuntimeCandidate,
        runtimeCommitSha: String,
        modelRevision: String,
        modelSha256: String,
        expectedArtifactBytes: Long,
        minimumAvailableMemoryBytes: Long,
    ): SocialAiArtifactProvenance {
        require(runtimeCommitSha.matches(Regex("[A-Fa-f0-9]{40}"))) {
            "Candidate runtime must be pinned to a full Git commit SHA."
        }
        require(modelRevision.isNotBlank())
        require(modelSha256.matches(Regex("[A-Fa-f0-9]{64}")))
        return SocialAiArtifactProvenance(
            runtime = OpenSourceComponentMetadata(
                candidate.runtimeComponentId,
                runtimeCommitSha,
                candidate.runtimeLicenseSpdx,
                candidate.runtimeSourceUrl,
            ),
            model = LocalModelArtifactMetadata(
                candidate.modelId,
                modelRevision,
                candidate.modelLicenseSpdx,
                modelSha256,
                candidate.modelSourceUrl,
            ),
            modelFormat = "GGUF",
            quantization = candidate.quantization,
            expectedModelBytes = expectedArtifactBytes,
            minimumRamBytes = minimumAvailableMemoryBytes,
        ).also(SocialAiOpenSourceAdmission::requireAdmitted)
    }
}
