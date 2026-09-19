package org.routingplatform.app.ai

private val TOOLCHAIN_COMPONENT_PATTERN = Regex("[A-Za-z0-9._+-]{1,96}")
private val TOOLCHAIN_VERSION_PATTERN = Regex("[A-Za-z0-9._+-]{1,64}")
private val SHA256_HEX_PATTERN = Regex("[A-Fa-f0-9]{64}")

data class SocialAiRuntimeBuildEvidence(
    val sourceSha256: String,
    val artifactSha256ByAbi: Map<String, String>,
    val toolchain: Map<String, String>,
    val buildArguments: List<String>,
    val sbomSha256: String,
) {
    init {
        require(SHA256_HEX_PATTERN.matches(sourceSha256)) { "Runtime source SHA-256 is required." }
        require(artifactSha256ByAbi.isNotEmpty()) { "At least one runtime artifact hash is required." }
        require(artifactSha256ByAbi.keys.all { it in setOf("arm64-v8a", "x86_64") })
        require(artifactSha256ByAbi.values.all(SHA256_HEX_PATTERN::matches))
        require(toolchain.isNotEmpty()) { "Pinned toolchain metadata is required." }
        require(toolchain.all { (name, version) ->
            TOOLCHAIN_COMPONENT_PATTERN.matches(name) && TOOLCHAIN_VERSION_PATTERN.matches(version) &&
                !version.equals("latest", true)
        }) { "Toolchain components and versions must be explicit and immutable." }
        require(buildArguments.isNotEmpty()) { "Reproducible build arguments are required." }
        require(buildArguments.all { it.isNotBlank() && it.length <= 256 })
        require(SHA256_HEX_PATTERN.matches(sbomSha256)) { "SBOM SHA-256 is required." }
    }
}

object SocialAiRuntimeDistributionGate {
    fun requireAdmitted(
        attestation: SocialAiRuntimeAttestation,
        evidence: SocialAiRuntimeBuildEvidence,
    ) {
        SocialAiOpenSourceAdmission.requireAdmitted(attestation.provenance)
        require(evidence.artifactSha256ByAbi.keys == attestation.supportedAbis) {
            "Runtime artifact hashes must cover exactly the attested Android ABIs."
        }
    }
}
