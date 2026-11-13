package org.routingplatform.app.navigation

data class NavigationRuntimeIntelligenceState(
    val lastDecision: NavigationDecisionTelemetry? = null,
    val pendingRecoveryRequestId: Long? = null,
    val dualAi: DualAiTrainingResult? = null,
)

internal class NavigationRuntimeIntelligence(
    private val decisionCoordinator: NavigationDecisionCoordinator = NavigationDecisionCoordinator(),
    private val telemetryBuilder: NavigationDecisionTelemetryBuilder = NavigationDecisionTelemetryBuilder(),
    private val dualAi: DualAiTrainingCoordinator = DualAiTrainingCoordinator(),
) {
    private var state = NavigationRuntimeIntelligenceState()

    fun evaluate(
        nowElapsedMs: Long,
        nowEpochMs: Long = nowElapsedMs,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
        liveEvents: List<LiveEventDecision>,
        nearestLiveEventDistanceMeters: Double?,
        sessionId: String = "navigation-session",
        routeVersion: Long = 0L,
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
        val localEnvelope = NavigationDecisionEnvelope(
            eventId = "navigation-$nowElapsedMs",
            sessionId = sessionId,
            routeVersion = routeVersion,
            capturedAtEpochMs = nowEpochMs,
            origin = AiDecisionOrigin.LOCAL,
            mode = AiDecisionMode.ACT,
            kind = if (decision.shouldRequestRecalculation == true) {
                AiDecisionKind.REROUTE
            } else {
                AiDecisionKind.CONTINUE
            },
            confidence = if (decision.shouldRequestRecalculation == true) 0.9 else 0.8,
            evidence = buildSet {
                add("local-navigation-decision")
                decision.recovery.action.name?.let { add(it) }
            },
            expiresAtEpochMs = nowEpochMs + 5_000L,
            safetyClass = 1,
            modelVersion = "local-navigation-v1",
        )
        val twin = NavigationTwinState(
            sessionId = sessionId,
            routeVersion = routeVersion,
            position = null,
            positionAccuracyMeters = null,
            speedMetersPerSecond = null,
            activeProviderVersions = emptyMap(),
            offline = false,
            lastDecisionEventId = state.dualAi?.accepted?.eventId,
        )
        val dualAiResult = dualAi.evaluate(nowEpochMs, twin, localEnvelope)
        state = NavigationRuntimeIntelligenceState(
            lastDecision = telemetry,
            pendingRecoveryRequestId = decision.recovery.requestId,
            dualAi = dualAiResult,
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




