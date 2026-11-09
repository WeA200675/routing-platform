package org.routingplatform.app.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock

/**
 * Converts the latest Android navigation telemetry into the scheduler input
 * contract. The mapper deliberately keeps timestamps nullable when no trusted
 * observation exists, so the watchdog cannot mistake missing data for fresh GPS.
 */
internal class NavigationRuntimeCycleInputMapper(
    context: Context,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val nowElapsedMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val networkAvailable: () -> Boolean = {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        manager?.activeNetwork?.let { network ->
            manager.getNetworkCapabilities(network)?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_INTERNET,
            ) == true
        } == true
    },
    private val batteryPercent: () -> Int = {
        val manager = context.getSystemService(BatteryManager::class.java)
        manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 0
    },
    private val speedMetersPerSecond: () -> Double = { 0.0 },
    private val nearestLiveEventDistanceMeters: () -> Double? = { null },
) {
    @Volatile
    private var telemetry: NavigationRuntimeTelemetry = NavigationRuntimeTelemetry.stopped()

    fun update(telemetry: NavigationRuntimeTelemetry) {
        this.telemetry = telemetry
    }

    fun inputs(navigationActive: Boolean): NavigationRuntimeCycleInputs {
        val current = telemetry
        val lastObservationMs = current.lastLocationElapsedRealtimeNanos
            ?.div(1_000_000L)
        return NavigationRuntimeCycleInputs(
            nowEpochMs = nowEpochMs(),
            nowElapsedMs = nowElapsedMs(),
            lastPositionElapsedMs = lastObservationMs,
            lastProgressElapsedMs = lastObservationMs
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
