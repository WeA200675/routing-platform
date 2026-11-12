package org.routingplatform.app.navigation

data class NavigationRuntimeCycleResult(
    val liveContext: NavigationLiveRuntimeSnapshot,
    val decision: NavigationRuntimeIntelligenceState,
    val nextRefreshInMs: Long,
)

class NavigationRuntimeCycle(
    private val liveRuntime: NavigationLiveRuntimeCoordinator,
    private val intelligence: NavigationRuntimeIntelligence,
) {
    fun run(
        nowEpochMs: Long,
        nowElapsedMs: Long,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
        speedMetersPerSecond: Double,
        networkAvailable: Boolean,
        batteryPercent: Int,
        nearestLiveEventDistanceMeters: Double?,
    ): NavigationRuntimeCycleResult {
        val context = LiveRefreshContext(
            navigationActive = navigationActive,
            speedMetersPerSecond = speedMetersPerSecond,
            networkAvailable = networkAvailable,
            batteryPercent = batteryPercent,
        )
        val live = if (NavigationLiveRefreshPolicy.intervalMs(context) == Long.MAX_VALUE) {
            liveRuntime.current(nowEpochMs)
        } else {
            liveRuntime.refresh(nowEpochMs)
        }
        val decision = intelligence.evaluate(
            nowElapsedMs = nowElapsedMs,
            nowEpochMs = nowEpochMs,
            lastPositionElapsedMs = lastPositionElapsedMs,
            lastProgressElapsedMs = lastProgressElapsedMs,
            navigationActive = navigationActive,
            liveEvents = live.prioritizedEvents,
            nearestLiveEventDistanceMeters = nearestLiveEventDistanceMeters,
        )
        return NavigationRuntimeCycleResult(
            liveContext = live,
            decision = decision,
            nextRefreshInMs = NavigationLiveRefreshPolicy.intervalMs(context),
        )
    }
}
