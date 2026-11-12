package org.routingplatform.app.ai

/** Combined acceptance result for evidence captured from one physical device run. */
data class SocialAiPhysicalDeviceGateEvidence(
    val performance: SocialAiDevicePerformanceEvidence,
    val safety: SocialAiDeviceSafetyEvidence,
    val runtimeArtifactSha256: String,
    val modelSha256: String,
)

object SocialAiPhysicalDeviceGate {
    fun validate(
        evidence: SocialAiPhysicalDeviceGateEvidence,
        releaseManifest: SocialAiRuntimeReleaseManifest,
        deviceAbi: String,
    ): List<String> = buildList {
        addAll(SocialAiDevicePerformanceGate.validate(evidence.performance))
        addAll(SocialAiDeviceSafetyGate.validate(evidence.safety))
        val expectedRuntime = releaseManifest.runtimeArtifactSha256ByAbi[deviceAbi]
        if (expectedRuntime == null ||
            !evidence.runtimeArtifactSha256.equals(expectedRuntime, ignoreCase = true)
        ) {
            add("Physical device runtime artifact does not match the reviewed release manifest.")
        }
        if (!evidence.modelSha256.equals(releaseManifest.modelSha256, ignoreCase = true)) {
            add("Physical device model artifact does not match the reviewed release manifest.")
        }
    }
}
