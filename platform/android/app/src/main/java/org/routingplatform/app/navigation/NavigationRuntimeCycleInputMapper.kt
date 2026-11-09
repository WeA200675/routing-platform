package org.routingplatform.app.navigation

/**
 * Converts the latest Android navigation telemetry into the scheduler input
 * contract. The mapper deliberately keeps timestamps nullable when no trusted
 * observation exists, so the watchdog cannot mistake missing data for fresh GPS.
 */
internal class NavigationRuntimeCycleInputMapper(
    private val nowEpochMs: () -> Long,
    private val nowElapsedMs: () -> Long,
    private val networkAvailable: () -> Boolean,
    private val batteryPercent: () -> Int,
    private val speedMetersPerSecond: () -> Double,
    private val nearestLiveEventDistanceMeters: () -> Double?,
) {
    @Volatile
    private var telemetry: NavigationRuntimeTelemetry = NavigationRuntimeTelemetry.stopped()

    fun update(telemetry: NavigationRuntimeTelemetry) {
        this.telemetry = telemetry
    }

    fun inputs(navigationActive: Boolean): NavigationRuntimeCycleInputs {
        val current = telemetry
        return NavigationRuntimeCycleInputs(
            nowEpochMs = nowEpochMs(),
            nowElapsedMs = nowElapsedMs(),
            lastPositionElapsedMs = current.lastLocationElapsedRealtimeNanos
                ?.div(1_000_000L),
            lastProgressElapsedMs = current.lastLocationElapsedRealtimeNanos
                ?.div(1_000_000L)
                ?.takeIf { current.acceptedProgress != null },
            navigationActive = navigationActive,
            speedMetersPerSecond = speedMetersPerSecond().coerceAtLeast(0.0),
            networkAvailable = networkAvailable(),
            batteryPercent = batteryPercent().coerceIn(0, 100),
            nearestLiveEventDistanceMeters = nearestLiveEventDistanceMeters()
                ?.takeIf { it.isFinite() && it >= 0.0 },
        )
    }
}
