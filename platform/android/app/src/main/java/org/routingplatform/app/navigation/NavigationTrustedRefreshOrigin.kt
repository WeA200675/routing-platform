package org.routingplatform.app.navigation

/**
 * Safety boundary for dynamic route refresh.
 *
 * The observation carries its monotonic timestamp so callers can prove
 * freshness at the instant a refresh is requested. Dead reckoning and
 * accepted route progress are never coordinate sources.
 */
internal data class NavigationTrustedRefreshObservation(
    val point: RoutePoint,
    val elapsedRealtimeNanos: Long,
)

internal object NavigationTrustedRefreshOrigin {
    const val MAX_AGE_NANOS: Long = 5_000_000_000L

    fun fromTelemetry(telemetry: NavigationRuntimeTelemetry): NavigationTrustedRefreshObservation? {
        if (telemetry.confidence != NavigationPositionConfidence.High) return null
        if (telemetry.fusionMode != NavigationFusionMode.DirectObservation) return null
        val point = telemetry.lastObservedPosition ?: return null
        val observedAt = telemetry.lastLocationElapsedRealtimeNanos ?: return null
        if (observedAt < 0L) return null
        return NavigationTrustedRefreshObservation(point, observedAt)
    }

    fun current(
        observation: NavigationTrustedRefreshObservation?,
        nowElapsedRealtimeNanos: Long,
    ): RoutePoint? {
        val value = observation ?: return null
        if (nowElapsedRealtimeNanos < value.elapsedRealtimeNanos) return null
        if (nowElapsedRealtimeNanos - value.elapsedRealtimeNanos > MAX_AGE_NANOS) return null
        return value.point
    }
}
