package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.navigation.NavigationDeviceCalibrationProfile

class NavigationCalibrationPresentationTest {
    @Test
    fun summaryShowsCountsAndBestAccuracy() {
        val profile = NavigationDeviceCalibrationProfile(
            acceptedDirectSamples = 12,
            rejectedSamples = 4,
            bestObservedAccuracyM = 3.24,
        )
        assertEquals(
            "12 direkt akzeptiert · 4 verworfen · beste Genauigkeit 3.2 m",
            NavigationCalibrationPresentation.summary(profile),
        )
    }

    @Test
    fun emptyProfileHasClearSummary() {
        assertEquals(
            "0 direkt akzeptiert · 0 verworfen · beste Genauigkeit keine",
            NavigationCalibrationPresentation.summary(NavigationDeviceCalibrationProfile()),
        )
    }
}
