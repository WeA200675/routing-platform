package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationProgressWatchdogTest {
    @Test
    fun healthyWhenPositionAndProgressAreFresh() {
        val result = NavigationProgressWatchdog().evaluate(10_000, 8_500, 8_000, true)
        assertEquals(NavigationWatchdogStatus.HEALTHY, result.status)
    }

    @Test
    fun distinguishesStalePositionFromStaleProgress() {
        val watchdog = NavigationProgressWatchdog()
        assertEquals(
            NavigationWatchdogStatus.POSITION_STALE,
            watchdog.evaluate(10_000, 6_000, 9_000, true).status,
        )
        assertEquals(
            NavigationWatchdogStatus.PROGRESS_STALE,
            watchdog.evaluate(10_000, 9_000, 3_000, true).status,
        )
    }

    @Test
    fun requestsRecoveryWhenBothSignalsAreStale() {
        val result = NavigationProgressWatchdog().evaluate(20_000, 1_000, 1_000, true)
        assertEquals(NavigationWatchdogStatus.RECOVERY_REQUIRED, result.status)
    }

    @Test
    fun remainsHealthyWhenNavigationIsInactive() {
        val result = NavigationProgressWatchdog().evaluate(20_000, null, null, false)
        assertEquals(NavigationWatchdogStatus.HEALTHY, result.status)
    }
}
