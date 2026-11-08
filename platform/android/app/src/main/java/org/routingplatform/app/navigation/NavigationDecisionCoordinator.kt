package org.routingplatform.app.navigation

data class NavigationDecisionSnapshot(
    val recovery: NavigationRecoveryDecision,
    val liveAction: LiveRouteAction,
    val shouldRequestRecalculation: Boolean,
)

class NavigationDecisionCoordinator(
    private val recovery: NavigationRecoveryCoordinator = NavigationRecoveryCoordinator(),
) {
    fun evaluate(
        nowElapsedMs: Long,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
        liveEvents: List<LiveEventDecision>,
        nearestLiveEventDistanceMeters: Double?,
    ): NavigationDecisionSnapshot {
        val recoveryDecision = recovery.evaluate(
            nowElapsedMs = nowElapsedMs,
            lastPositionElapsedMs = lastPositionElapsedMs,
            lastProgressElapsedMs = lastProgressElapsedMs,
            navigationActive = navigationActive,
        )
        val liveAction = NavigationLiveRoutePolicy.decide(
            events = liveEvents,
            nearestEventDistanceMeters = nearestLiveEventDistanceMeters,
        )
        return NavigationDecisionSnapshot(
            recovery = recoveryDecision,
            liveAction = liveAction,
            shouldRequestRecalculation =
                recoveryDecision.action == NavigationRecoveryAction.REQUEST_RECALCULATION ||
                    liveAction == LiveRouteAction.REQUEST_IMMEDIATE_RECALCULATION,
        )
    }

    fun completeRecovery(requestId: Long) {
        recovery.complete(requestId)
    }
}
