package org.routingplatform.app.ai

/**
 * Mutable counter-only recorder used during the manual physical-device run.
 * It intentionally cannot record prompts, generated text, route facts or locations.
 */
class SocialAiDeviceSafetyRecorder {
    private var offlineRequests = 0
    private var offlineFailures = 0
    private var navigationStatusChecks = 0
    private var navigationAuthorityMutations = 0
    private var cancellationChecks = 0
    private var unverifiedPartialOutputs = 0
    private var backgroundForegroundCycles = 0
    private var lifecycleFailures = 0

    fun recordOfflineRequest(succeeded: Boolean) {
        offlineRequests++
        if (!succeeded) offlineFailures++
    }

    fun recordNavigationStatusCheck(authorityMutated: Boolean) {
        navigationStatusChecks++
        if (authorityMutated) navigationAuthorityMutations++
    }

    fun recordCancellation(unverifiedPartialOutputPresented: Boolean) {
        cancellationChecks++
        if (unverifiedPartialOutputPresented) unverifiedPartialOutputs++
    }

    fun recordBackgroundForegroundCycle(succeeded: Boolean) {
        backgroundForegroundCycles++
        if (!succeeded) lifecycleFailures++
    }

    fun snapshot(): SocialAiDeviceSafetyEvidence = SocialAiDeviceSafetyEvidence(
        offlineRequests, offlineFailures, navigationStatusChecks,
        navigationAuthorityMutations, cancellationChecks, unverifiedPartialOutputs,
        backgroundForegroundCycles, lifecycleFailures,
    )
}
