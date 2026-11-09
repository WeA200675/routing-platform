package org.routingplatform.app.navigation

import org.routingplatform.app.training.NavigationTrainingRuntimeScheduler

/**
 * Owns the navigation intelligence loop for one navigation session.
 *
 * The host supplies live sensor/network values through [inputs]. Every cycle
 * is persisted to the training journal by [trainingScheduler]. Recovery IDs are
 * emitted exactly once to the routing engine callback.
 */
internal class NavigationRuntimeLifecycleBinding(
    private val trainingScheduler: NavigationTrainingRuntimeScheduler,
    private val onRecoveryRequest: (Long) -> Unit,
) {
    private var lastRecoveryRequestId: Long? = null

    fun start(
        inputs: () -> NavigationRuntimeCycleInputs,
        batteryPercent: () -> Int,
        networkAvailable: () -> Boolean,
        navigationActive: () -> Boolean,
        sessionId: () -> String? = { null },
        routeId: () -> String? = { null },
        onResult: (NavigationRuntimeCycleResult) -> Unit = {},
    ) {
        lastRecoveryRequestId = null
        trainingScheduler.start(
            inputs = inputs,
            batteryPercent = batteryPercent,
            networkAvailable = networkAvailable,
            navigationActive = navigationActive,
            sessionId = sessionId,
            routeId = routeId,
        ) { result ->
            val requestId = result.decision.pendingRecoveryRequestId
            if (requestId != null && requestId != lastRecoveryRequestId) {
                lastRecoveryRequestId = requestId
                onRecoveryRequest(requestId)
            }
            onResult(result)
        }
    }

    fun stop() {
        trainingScheduler.stop()
        lastRecoveryRequestId = null
    }
}
