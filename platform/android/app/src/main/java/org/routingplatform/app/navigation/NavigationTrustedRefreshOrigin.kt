package org.routingplatform.app.navigation

/**
 * Safety boundary for dynamic route refresh.
 *
 * A refresh origin is admitted only from a current direct GNSS/fused
 * observation with High confidence. Accepted route progress, dead reckoning
 * and presentation bearings are deliberately not coordinate sources.
 */
internal object NavigationTrustedRefreshOrigin {
    fun fromTelemetry(telemetry: NavigationRuntimeTelemetry): RoutePoint? {
        if (telemetry.confidence != NavigationPositionConfidence.High) return null
        if (telemetry.fusionMode != NavigationFusionMode.DirectObservation) return null
        return telemetry.lastObservedPosition
    }
}
