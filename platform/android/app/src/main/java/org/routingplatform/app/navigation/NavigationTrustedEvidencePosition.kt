package org.routingplatform.app.navigation

import org.routingplatform.app.security.EvidenceVehiclePosition

/**
 * Reuses the same direct-observation/freshness boundary as route refresh.
 * The resulting coordinate is the vehicle observation, never source attribution.
 */
internal object NavigationTrustedEvidencePosition {
    fun fromTelemetry(
        telemetry: NavigationRuntimeTelemetry,
        nowElapsedRealtimeNanos: Long,
    ): EvidenceVehiclePosition? {
        val observation = NavigationTrustedRefreshOrigin.fromTelemetry(telemetry) ?: return null
        val point = NavigationTrustedRefreshOrigin.current(observation, nowElapsedRealtimeNanos) ?: return null
        val accuracy = telemetry.horizontalAccuracyM ?: return null
        if (!accuracy.isFinite() || accuracy < 0.0) return null
        return EvidenceVehiclePosition(
            latitude = point.latitude,
            longitude = point.longitude,
            accuracyM = accuracy,
            observedElapsedRealtimeNanos = observation.elapsedRealtimeNanos,
        )
    }
}
