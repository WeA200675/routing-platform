package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationObservationAdmissionTest {
    @Test fun requiresAllPlatformCapabilities() {
        assertTrue(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, true, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(false, true, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, false, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, true, false)))
    }

    @Test fun onlyHighDirectBoundedObservationIsAdmitted() {
        val valid = NavigationCalibrationObservation(
            NavigationPositionConfidence.High,
            NavigationFusionMode.DirectObservation,
            4.0,
            1L,
        )
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(confidence = NavigationPositionConfidence.Medium)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(fusionMode = NavigationFusionMode.FusedEstimate)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = null)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = Double.NaN)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = Double.POSITIVE_INFINITY)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = -0.1)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 100.1)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(elapsedRealtimeNanos = null)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(elapsedRealtimeNanos = -1L)))
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 0.0, elapsedRealtimeNanos = 0L)))
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 100.0)))
    }
}
