package org.routingplatform.app.ai

/**
 * Complete release acceptance record. It deliberately separates static
 * software admission from evidence that can only come from a physical device.
 */
data class SocialAiReleaseAcceptance(
    val distributionAdmitted: Boolean,
    val packagedArtifactsAdmitted: Boolean,
    val physicalDeviceViolations: List<String>?,
) {
    val softwareReady: Boolean
        get() = distributionAdmitted && packagedArtifactsAdmitted

    val mergeReady: Boolean
        get() = softwareReady && physicalDeviceViolations?.isEmpty() == true
}

object SocialAiReleaseAcceptanceGate {
    fun evaluate(
        attestation: SocialAiRuntimeAttestation,
        buildEvidence: SocialAiRuntimeBuildEvidence,
        manifest: SocialAiRuntimeReleaseManifest,
        packagedArtifacts: SocialAiPackagedNativeArtifacts,
        physicalEvidence: SocialAiPhysicalDeviceGateEvidence?,
        deviceAbi: String,
    ): SocialAiReleaseAcceptance {
        SocialAiRuntimeDistributionGate.requireAdmitted(attestation, buildEvidence)
        SocialAiRuntimeReleaseGate.requireAdmitted(manifest, attestation, buildEvidence)
        SocialAiPackagedNativeArtifactGate.requireAdmitted(packagedArtifacts, manifest)
        val physicalViolations = physicalEvidence?.let {
            SocialAiPhysicalDeviceGate.validate(it, manifest, deviceAbi)
        }
        return SocialAiReleaseAcceptance(
            distributionAdmitted = true,
            packagedArtifactsAdmitted = true,
            physicalDeviceViolations = physicalViolations,
        )
    }
}
