package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationRuntimeCycleResult

/**
 * Production boundary between the navigation cycle and the training journal.
 * MainActivity (or the navigation service) calls [onCycle] after each real
 * runtime cycle. Decision telemetry is persisted before the best-effort upload,
 * so offline operation never loses evidence.
 */
internal class NavigationTrainingRuntimeAdapter(
    private val bridge: TrainingRuntimeBridge,
    private val syncCoordinator: TrainingSyncCoordinator,
) {
    fun onCycle(
        result: NavigationRuntimeCycleResult,
        networkAvailable: Boolean,
        navigationActive: Boolean,
        batteryPercent: Int,
        sessionId: String? = null,
        routeId: String? = null,
    ): NavigationTrainingCycleResult {
        val telemetry = result.decision.lastDecision
            ?: return NavigationTrainingCycleResult.NoDecision
        val event = NavigationDecisionTrainingEventAdapter.toTrainingEvent(
            telemetry = telemetry,
            sessionId = sessionId,
            routeId = routeId,
        )
        bridge.recordDecisionEvent(event)
        val sync = syncCoordinator.sync(
            networkAvailable = networkAvailable,
            navigationActive = navigationActive,
            queuedEvents = 1,
            batteryPercent = batteryPercent,
        )
        return NavigationTrainingCycleResult.Recorded(
            eventId = event.eventId,
            sync = sync,
        )
    }
}

internal sealed interface NavigationTrainingCycleResult {
    data object NoDecision : NavigationTrainingCycleResult
    data class Recorded(
        val eventId: String,
        val sync: TrainingSyncResult,
    ) : NavigationTrainingCycleResult
}
