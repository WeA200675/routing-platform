package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.ui.NavigationVoiceCueStage

/**
 * Local, non-blocking performance telemetry.
 *
 * This class only observes navigation state. It never requests a reroute,
 * changes route costs, or blocks the navigation loop.
 */
internal data class NavigationPerformanceSample(
    val schemaVersion: String = "performance-v1",
    val sessionId: String,
    val routeId: String,
    val capturedAtElapsedMs: Long,
    val routeDistanceM: Double,
    val routeDurationS: Double,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,
    val speedMps: Double?,
    val sessionState: NavigationSessionState,
    val maneuverType: String?,
    val maneuverDistanceM: Double?,
    val voiceStage: NavigationVoiceCueStage?,
    val fallbackUsed: Boolean,
    val progressDeltaM: Double?,
    val durationDeltaS: Double?,
)

internal class NavigationPerformanceTelemetry(
    private val minimumIntervalMs: Long = 1_000L,
) {
    private var lastCapturedAtElapsedMs: Long? = null
    private var previousRemainingDistanceM: Double? = null
    private var previousRemainingDurationS: Double? = null
    private var activeSessionId: String? = null
    private var activeRouteId: String? = null

    fun record(
        snapshot: NavigationUiSnapshot,
        capturedAtElapsedMs: Long,
        voiceStage: NavigationVoiceCueStage? = null,
        fallbackUsed: Boolean = false,
    ): NavigationPerformanceSample? {
        if (capturedAtElapsedMs < 0L) return null

        if (
            activeSessionId != snapshot.sessionId ||
            activeRouteId != snapshot.routeId
        ) {
            reset()
            activeSessionId = snapshot.sessionId
            activeRouteId = snapshot.routeId
        }

        val lastCaptured = lastCapturedAtElapsedMs
        if (
            lastCaptured != null &&
            capturedAtElapsedMs - lastCaptured < minimumIntervalMs
        ) {
            return null
        }

        val previousDistance = previousRemainingDistanceM
        val previousDuration = previousRemainingDurationS
        val progressDelta =
            previousDistance?.let { it - snapshot.remainingDistanceM }
        val durationDelta =
            previousDuration?.let { it - snapshot.remainingDurationS }

        val maneuver = snapshot.currentManeuver
        val sample = NavigationPerformanceSample(
            sessionId = snapshot.sessionId,
            routeId = snapshot.routeId,
            capturedAtElapsedMs = capturedAtElapsedMs,
            routeDistanceM = snapshot.routeDistanceM,
            routeDurationS = snapshot.routeDurationS,
            remainingDistanceM = snapshot.remainingDistanceM,
            remainingDurationS = snapshot.remainingDurationS,
            speedMps = snapshot.currentSpeedMps,
            sessionState = snapshot.state,
            maneuverType = maneuver?.type?.name,
            maneuverDistanceM = maneuver?.distanceM,
            voiceStage = voiceStage,
            fallbackUsed = fallbackUsed,
            progressDeltaM = progressDelta,
            durationDeltaS = durationDelta,
        )

        lastCapturedAtElapsedMs = capturedAtElapsedMs
        previousRemainingDistanceM = snapshot.remainingDistanceM
        previousRemainingDurationS = snapshot.remainingDurationS
        return sample
    }

    fun reset() {
        lastCapturedAtElapsedMs = null
        previousRemainingDistanceM = null
        previousRemainingDurationS = null
        activeSessionId = null
        activeRouteId = null
    }
}
