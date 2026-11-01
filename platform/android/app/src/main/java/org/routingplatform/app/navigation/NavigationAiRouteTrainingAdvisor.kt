package org.routingplatform.app.navigation

enum class NavigationAiRouteTrainingSignal {
    Inactive,
    Observing,
    StabilizationEvidence,
    RequestDeterministicRerouteEvidence,
}

data class NavigationAiRouteTrainingSnapshot(
    val sessionId: String? = null,
    val samples: Int = 0,
    val instabilitySamples: Int = 0,
    val signal: NavigationAiRouteTrainingSignal = NavigationAiRouteTrainingSignal.Inactive,
)

/** AI learning signal only; the deterministic reroute engine remains authoritative. */
class NavigationAiRouteTrainingAdvisor(
    private val instabilityThreshold: Int = 3,
) {
    init { require(instabilityThreshold >= 2) }
    private var snapshot = NavigationAiRouteTrainingSnapshot()

    @Synchronized
    fun start(sessionId: String) {
        require(sessionId.isNotBlank())
        snapshot = NavigationAiRouteTrainingSnapshot(sessionId = sessionId, signal = NavigationAiRouteTrainingSignal.Observing)
    }

    @Synchronized
    fun observe(telemetry: NavigationRuntimeTelemetry, sessionId: String): NavigationAiRouteTrainingSignal {
        if (snapshot.sessionId != sessionId) start(sessionId)
        val unstable = telemetry.safetyStatus == NavigationRouteProgressSafetyStatus.HeldOffRoute
        val count = snapshot.instabilitySamples + if (unstable) 1 else 0
        val signal = when {
            count >= instabilityThreshold -> NavigationAiRouteTrainingSignal.RequestDeterministicRerouteEvidence
            unstable -> NavigationAiRouteTrainingSignal.StabilizationEvidence
            else -> NavigationAiRouteTrainingSignal.Observing
        }
        snapshot = snapshot.copy(samples = snapshot.samples + 1, instabilitySamples = count, signal = signal)
        return signal
    }

    @Synchronized fun reset() { snapshot = NavigationAiRouteTrainingSnapshot() }
    fun snapshot(): NavigationAiRouteTrainingSnapshot = snapshot
}
