package org.routingplatform.app.navigation

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class NavigationRuntimeCycleScheduler(
    private val executor: ScheduledExecutorService,
    private val cycle: NavigationRuntimeCycle,
) {
    private var future: ScheduledFuture<*>? = null

    @Synchronized
    fun start(
        inputs: () -> NavigationRuntimeCycleInputs,
        onResult: (NavigationRuntimeCycleResult) -> Unit,
    ) {
        stop()
        fun runOnce() {
            val input = inputs()
            val result = cycle.run(
                nowEpochMs = input.nowEpochMs,
                nowElapsedMs = input.nowElapsedMs,
                lastPositionElapsedMs = input.lastPositionElapsedMs,
                lastProgressElapsedMs = input.lastProgressElapsedMs,
                navigationActive = input.navigationActive,
                speedMetersPerSecond = input.speedMetersPerSecond,
                networkAvailable = input.networkAvailable,
                batteryPercent = input.batteryPercent,
                nearestLiveEventDistanceMeters = input.nearestLiveEventDistanceMeters,
            )
            onResult(result)
            val next = result.nextRefreshInMs
            if (next != Long.MAX_VALUE) {
                future = executor.schedule(::runOnce, next, TimeUnit.MILLISECONDS)
            }
        }
        future = executor.schedule(::runOnce, 0L, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun stop() {
        future?.cancel(false)
        future = null
    }
}

data class NavigationRuntimeCycleInputs(
    val nowEpochMs: Long,
    val nowElapsedMs: Long,
    val lastPositionElapsedMs: Long?,
    val lastProgressElapsedMs: Long?,
    val navigationActive: Boolean,
    val speedMetersPerSecond: Double,
    val networkAvailable: Boolean,
    val batteryPercent: Int,
    val nearestLiveEventDistanceMeters: Double?,
)

