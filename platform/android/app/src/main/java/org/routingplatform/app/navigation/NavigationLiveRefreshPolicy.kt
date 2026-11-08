package org.routingplatform.app.navigation

data class LiveRefreshContext(
    val navigationActive: Boolean,
    val speedMetersPerSecond: Double,
    val networkAvailable: Boolean,
    val batteryPercent: Int,
)

object NavigationLiveRefreshPolicy {
    fun intervalMs(context: LiveRefreshContext): Long {
        if (!context.networkAvailable) return Long.MAX_VALUE
        if (!context.navigationActive) return 300_000L
        val speed = context.speedMetersPerSecond.coerceAtLeast(0.0)
        val base = when {
            speed >= 27.8 -> 15_000L
            speed >= 13.9 -> 30_000L
            else -> 60_000L
        }
        return if (context.batteryPercent <= 15) base * 2 else base
    }
}
