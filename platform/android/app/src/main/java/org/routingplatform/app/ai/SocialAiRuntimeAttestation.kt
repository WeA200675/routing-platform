package org.routingplatform.app.ai

/** Immutable attestation tying a reviewed runtime/model pair to one build. */
data class SocialAiRuntimeAttestation(
    val provenance: SocialAiArtifactProvenance,
    val buildId: String,
    val supportedAbis: Set<String>,
) {
    init {
        require(buildId.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) {
            "buildId must be an immutable safe identifier."
        }
        require(supportedAbis.isNotEmpty()) { "At least one Android ABI must be attested." }
        require(supportedAbis.all { it in ALLOWED_ANDROID_ABIS }) {
            "Unsupported Android ABI in runtime attestation."
        }
    }

    companion object {
        private val ALLOWED_ANDROID_ABIS = setOf("arm64-v8a", "x86_64")
    }
}

object SocialAiRuntimeAttestationGate {
    fun requireAdmitted(
        attestation: SocialAiRuntimeAttestation,
        backend: LocalSocialAiTextGenerationBackend,
        deviceAbi: String,
    ) {
        SocialAiOpenSourceAdmission.requireAdmitted(attestation.provenance)
        require(attestation.provenance.runtime == backend.runtimeMetadata) {
            "Attested runtime metadata does not match backend runtime metadata."
        }
        require(attestation.provenance.model == backend.modelMetadata) {
            "Attested model metadata does not match backend model metadata."
        }
        require(deviceAbi in attestation.supportedAbis) {
            "Current device ABI is not covered by the runtime attestation."
        }
    }
}
