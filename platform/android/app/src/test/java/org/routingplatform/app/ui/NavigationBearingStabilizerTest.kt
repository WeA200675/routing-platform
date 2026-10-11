package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBearingStabilizerTest {
    @Test
    fun ignoresSmallHeadingNoise() {
        val stabilizer = NavigationBearingStabilizer()

        assertEquals(90.0, stabilizer.update(90.0), 0.001)
        assertEquals(90.0, stabilizer.update(91.0), 0.001)
        assertEquals(90.0, stabilizer.update(89.0), 0.001)
    }

    @Test
    fun smoothsLargeChangesAlongShortestDirectionAcrossNorth() {
        val stabilizer = NavigationBearingStabilizer(smoothingFactor = 0.35)

        assertEquals(350.0, stabilizer.update(350.0), 0.001)
        assertEquals(355.25, stabilizer.update(5.0), 0.001)
    }

    @Test
    fun resetMakesNorthUpImmediateAndNextHeadingInitializesCleanly() {
        val stabilizer = NavigationBearingStabilizer()

        stabilizer.update(135.0)
        stabilizer.reset()

        assertEquals(0.0, stabilizer.update(1.0), 0.001)
        assertEquals(31.5, stabilizer.update(90.0), 0.001)
    }
}
