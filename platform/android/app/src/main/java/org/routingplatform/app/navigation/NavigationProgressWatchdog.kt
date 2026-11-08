package org.routingplatform.app.navigation

enum class NavigationWatchdogStatus {
    HEALTHY,
    POSITION_STALE,
    PROGRESS_STALE,
    RECOVERY_REQUIRED,
}

data class NavigationWatchdogSnapshot(
    val status: NavigationWatchdogStatus,
    val ageSincePositionMs: Long,
    val ageSinceProgressMs: Long,
    val reason: String? = null,
)

class NavigationProgressWatchdog(
    private val maxPositionAgeMs: Long = 3_000L,
    private val maxProgressAgeMs: Long = 6_000L,
    private val recoveryAfterMs: Long = 12_000L,
) {
    init {
        require(maxPositionAgeMs > 0)
        require(maxProgressAgeMs >= maxPositionAgeMs)
        require(recoveryAfterMs >= maxProgressAgeMs)
    }

    fun evaluate(
        nowElapsedMs: Long,
        lastPositionElapsedMs: Long?,
        lastProgressElapsedMs: Long?,
        navigationActive: Boolean,
    ): NavigationWatchdogSnapshot {
        if (!navigationActive) {
            return NavigationWatchdogSnapshot(NavigationWatchdogStatus.HEALTHY, 0L, 0L)
        }

        val positionAge = age(nowElapsedMs, lastPositionElapsedMs)
        val progressAge = age(nowElapsedMs, lastProgressElapsedMs)

        return when {
            positionAge >= recoveryAfterMs && progressAge >= recoveryAfterMs ->
                NavigationWatchdogSnapshot(
                    NavigationWatchdogStatus.RECOVERY_REQUIRED,
                    positionAge,
                    progressAge,
                    "Position und Routenfortschritt sind veraltet",
                )
            positionAge > maxPositionAgeMs ->
                NavigationWatchdogSnapshot(
                    NavigationWatchdogStatus.POSITION_STALE,
                    positionAge,
                    progressAge,
                    "Keine aktuelle Positionsmessung",
                )
            progressAge > maxProgressAgeMs ->
                NavigationWatchdogSnapshot(
                    NavigationWatchdogStatus.PROGRESS_STALE,
                    positionAge,
                    progressAge,
                    "Kein bestätigter Routenfortschritt",
                )
            else ->
                NavigationWatchdogSnapshot(
                    NavigationWatchdogStatus.HEALTHY,
                    positionAge,
                    progressAge,
                )
        }
    }

    private fun age(now: Long, last: Long?): Long =
        if (last == null || now < last) Long.MAX_VALUE else now - last
}
