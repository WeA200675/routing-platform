package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveRefreshPolicyTest {
    @Test
    fun refreshesMoreOftenAtHighSpeed() {
        val slow = NavigationLiveRefreshPolicy.intervalMs(
            LiveRefreshContext(true, 5.0, true, 80),
        )
        val fast = NavigationLiveRefreshPolicy.intervalMs(
            LiveRefreshContext(true, 30.0, true, 80),
        )
        assertEquals(true, fast < slow)
    }

    @Test
    fun disablesNetworkRefreshWhenOffline() {
        assertEquals(
            Long.MAX_VALUE,
            NavigationLiveRefreshPolicy.intervalMs(
                LiveRefreshContext(true, 20.0, false, 80),
            ),
        )
    }

    @Test
    fun reducesLoadWhenBatteryIsLow() {
        val normal = NavigationLiveRefreshPolicy.intervalMs(
            LiveRefreshContext(true, 20.0, true, 80),
        )
        val low = NavigationLiveRefreshPolicy.intervalMs(
            LiveRefreshContext(true, 20.0, true, 10),
        )
        assertEquals(normal * 2, low)
    }
}
