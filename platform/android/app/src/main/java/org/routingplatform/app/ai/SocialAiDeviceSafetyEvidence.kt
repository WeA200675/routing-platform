package org.routingplatform.app.ai

/** Deterministic acceptance record for the physical-device safety gate. */
data class SocialAiDeviceSafetyEvidence(
    val offlineRequests: Int,
    val offlineFailures: Int,
    val navigationStatusChecks: Int,
    val navigationAuthorityMutations: Int,
    val cancellationChecks: Int,
    val unverifiedPartialOutputs: Int,
    val backgroundForegroundCycles: Int,
    val lifecycleFailures: Int,
) {
    init {
        require(offlineRequests >= 1)
        require(offlineFailures >= 0)
        require(navigationStatusChecks >= 10)
        require(navigationAuthorityMutations >= 0)
        require(cancellationChecks >= 10)
        require(unverifiedPartialOutputs >= 0)
        require(backgroundForegroundCycles >= 5)
        require(lifecycleFailures >= 0)
    }
}

object SocialAiDeviceSafetyGate {
    fun validate(evidence: SocialAiDeviceSafetyEvidence): List<String> = buildList {
        if (evidence.offlineFailures != 0) add("Offline inference failures were observed.")
        if (evidence.navigationAuthorityMutations != 0) {
            add("Generated text affected deterministic navigation authority.")
        }
        if (evidence.unverifiedPartialOutputs != 0) {
            add("Unverified partial model output was presented.")
        }
        if (evidence.lifecycleFailures != 0) {
            add("Background/foreground lifecycle failures were observed.")
        }
    }
}
