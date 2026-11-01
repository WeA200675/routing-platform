package org.routingplatform.app.ai

import android.content.Context
import org.routingplatform.app.navigation.NavigationRouteProgressSafetyStatus
import org.routingplatform.app.navigation.NavigationRuntimeTelemetry

enum class SocialAiRouteTrainingSignal {
    Inactive,
    Observing,
    StabilizationEvidence,
    RequestDeterministicRerouteEvidence,
    Stopped,
}

data class SocialAiRouteTrainingSnapshot(
    val sessionId: String? = null,
    val samples: Int = 0,
    val instabilitySamples: Int = 0,
    val signal: SocialAiRouteTrainingSignal = SocialAiRouteTrainingSignal.Inactive,
)

/** Shadow-learning runtime. It can recommend evidence collection, never invent a route. */
class SocialAiRouteTrainingRuntime(private val context: Context) {
    @Volatile private var snapshot = SocialAiRouteTrainingSnapshot()

    @Synchronized
    fun start(sessionId: String) {
        require(sessionId.isNotBlank())
        snapshot = SocialAiRouteTrainingSnapshot(sessionId = sessionId, signal = SocialAiRouteTrainingSignal.Observing)
    }

    @Synchronized
    fun observe(telemetry: NavigationRuntimeTelemetry): SocialAiRouteTrainingSignal {
        val current = snapshot
        if (current.sessionId == null) return SocialAiRouteTrainingSignal.Inactive
        val control = SocialAiDevelopmentControlStore.from(context)
        if (runCatching { control.requireAllowed(SocialAiOperation.Training) }.isFailure) {
            snapshot = current.copy(signal = SocialAiRouteTrainingSignal.Stopped)
            return snapshot.signal
        }
        val unstable = telemetry.safetyStatus == NavigationRouteProgressSafetyStatus.HeldOffRoute
        val instabilitySamples = current.instabilitySamples + if (unstable) 1 else 0
        val signal = when {
            instabilitySamples >= 3 -> SocialAiRouteTrainingSignal.RequestDeterministicRerouteEvidence
            unstable -> SocialAiRouteTrainingSignal.StabilizationEvidence
            else -> SocialAiRouteTrainingSignal.Observing
        }
        snapshot = current.copy(samples = current.samples + 1, instabilitySamples = instabilitySamples, signal = signal)
        return signal
    }

    @Synchronized fun stop() { snapshot = SocialAiRouteTrainingSnapshot() }
    fun snapshot(): SocialAiRouteTrainingSnapshot = snapshot
}
