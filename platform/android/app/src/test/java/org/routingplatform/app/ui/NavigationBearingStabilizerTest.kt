package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBearingStabilizerTest {
    @Test
    fun firstTrustedHeadingSnapsToRouteAtStartup() {
        val stabilizer = NavigationBearingStabilizer()
        assertEquals(350.0, stabilizer.update(350.0, 0L), 0.001)
    }

    @Test
    fun ignoresSmallHeadingNoise() {
        val stabilizer = NavigationBearingStabilizer()
        assertEquals(90.0, stabilizer.update(90.0, 0L), 0.001)
        assertEquals(90.0, stabilizer.update(91.0, 500L), 0.001)
        assertEquals(90.0, stabilizer.update(89.0, 1_000L), 0.001)
    }

    @Test
    fun smoothsAcrossNorthUsingShortestDirection() {
        val stabilizer = NavigationBearingStabilizer()
        assertEquals(350.0, stabilizer.update(350.0, 0L), 0.001)
        assertEquals(355.25, stabilizer.update(5.0, 500L), 0.001)
    }

    @Test
    fun boundsLargeTurnsByElapsedTime() {
        val stabilizer = NavigationBearingStabilizer()
        assertEquals(0.0, stabilizer.update(0.0, 0L), 0.001)
        assertEquals(9.0, stabilizer.update(90.0, 100L), 0.001)
        assertEquals(18.0, stabilizer.update(90.0, 200L), 0.001)
    }

    @Test
    fun resetAllowsNextHeadingToSnapAtNavigationStart() {
        val stabilizer = NavigationBearingStabilizer()
        stabilizer.update(135.0, 0L)
        stabilizer.reset()
        assertEquals(1.0, stabilizer.update(1.0, 1_000L), 0.001)
        assertEquals(32.15, stabilizer.update(90.0, 1_500L), 0.001)
    }
}
