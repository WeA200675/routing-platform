package org.routingplatform.app.ai

private val RELEASE_SHA256_PATTERN = Regex("[A-Fa-f0-9]{64}")

/**
 * Immutable release manifest tying the selected model and per-ABI native
 * artifacts to the already-reviewed attestation and reproducible build evidence.
 */
data class SocialAiRuntimeReleaseManifest(
    val runtimeComponentId: String,
    val runtimeRevision: String,
    val modelId: String,
    val modelRevision: String,
    val modelSha256: String,
    val runtimeArtifactSha256ByAbi: Map<String, String>,
    val sbomSha256: String,
) {
    init {
        require(runtimeComponentId.isNotBlank())
        require(runtimeRevision.isNotBlank())
        require(modelId.isNotBlank())
        require(modelRevision.isNotBlank())
        require(RELEASE_SHA256_PATTERN.matches(modelSha256))
        require(runtimeArtifactSha256ByAbi.isNotEmpty())
        require(runtimeArtifactSha256ByAbi.values.all(RELEASE_SHA256_PATTERN::matches))
        require(RELEASE_SHA256_PATTERN.matches(sbomSha256))
    }
}

object SocialAiRuntimeReleaseGate {
    fun requireAdmitted(
        manifest: SocialAiRuntimeReleaseManifest,
        attestation: SocialAiRuntimeAttestation,
        evidence: SocialAiRuntimeBuildEvidence,
    ) {
        val provenance = attestation.provenance
        require(manifest.runtimeComponentId == provenance.runtime.componentId)
        require(manifest.runtimeRevision == provenance.runtime.revision)
        require(manifest.modelId == provenance.model.modelId)
        require(manifest.modelRevision == provenance.model.revision)
        require(manifest.modelSha256.equals(provenance.model.sha256, ignoreCase = true))
        require(manifest.runtimeArtifactSha256ByAbi.mapValues { it.value.lowercase() } ==
            evidence.artifactSha256ByAbi.mapValues { it.value.lowercase() }) {
            "Release manifest runtime artifacts do not match reviewed build evidence."
        }
        require(manifest.sbomSha256.equals(evidence.sbomSha256, ignoreCase = true)) {
            "Release manifest SBOM does not match reviewed build evidence."
        }
    }
}
