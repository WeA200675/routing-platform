package org.routingplatform.app.navigation

data class NavigationRuntimeIntelligenceState(
    val lastDecision: NavigationDecisionTelemetry? = null,
    val pendingRecoveryRequestId: Long? = null,
)

class NavigationRuntimeIntelligence(
    private val decisionCoordinator: NavigationDecisionCoordinator = NavigationDecisionCoordinator(),
    private val telemetryBuilder: NavigationDecisionTelemetryBuilder = NavigationDecisionTelemetryBuilder(),
) {
    private var state = NavigationRuntimeIntelligenceState()

    fun evaluate(
        nowElapsedMs: Long,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
        liveEvents: List<LiveEventDecision>,
        nearestLiveEventDistanceMeters: Double?,
    ): NavigationRuntimeIntelligenceState {
        val decision = decisionCoordinator.evaluate(
            nowElapsedMs = nowElapsedMs,
            lastPositionElapsedMs = lastPositionElapsedMs,
            lastProgressElapsedMs = lastProgressElapsedMs,
            navigationActive = navigationActive,
            liveEvents = liveEvents,
            nearestLiveEventDistanceMeters = nearestLiveEventDistanceMeters,
        )
        val telemetry = telemetryBuilder.build(nowElapsedMs, decision)
        state = NavigationRuntimeIntelligenceState(
            lastDecision = telemetry,
            pendingRecoveryRequestId = decision.recovery.requestId,
        )
        return state
    }

    fun completeRecovery(requestId: Long): NavigationRuntimeIntelligenceState {
        decisionCoordinator.completeRecovery(requestId)
        state = state.copy(pendingRecoveryRequestId = null)
        return state
    }

    fun currentState(): NavigationRuntimeIntelligenceState = state
}
