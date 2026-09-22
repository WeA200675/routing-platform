package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationCalibrationStoreTest {
    private val good = NavigationDeviceCalibrationProfile(acceptedDirectSamples = 4, bestObservedAccuracyM = 3.0)

    @Test fun freshProfileIsUsable() {
        assertEquals(good, NavigationCalibrationFreshness.usable(PersistedNavigationCalibration(good, 1000), 2000))
    }

    @Test fun futureProfileFailsClosed() {
        assertEquals(NavigationDeviceCalibrationProfile(), NavigationCalibrationFreshness.usable(PersistedNavigationCalibration(good, 2001), 2000))
    }

    @Test fun staleProfileFailsClosed() {
        val now = NavigationCalibrationFreshness.MAX_AGE_MILLIS + 1001
        assertEquals(NavigationDeviceCalibrationProfile(), NavigationCalibrationFreshness.usable(PersistedNavigationCalibration(good, 1000), now))
    }
}
