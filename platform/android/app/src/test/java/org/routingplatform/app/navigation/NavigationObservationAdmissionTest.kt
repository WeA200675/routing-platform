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

    @Test fun healthSnapshotFailsClosedWithoutObservation() {
        val capabilities = NavigationDeviceCapabilities(true, true, true)
        val health = NavigationObservationAdmission.healthSnapshot(capabilities, null)

        assertTrue(health.calibrationAvailable)
        assertFalse(health.directFreshObservationAvailable)
    }

    @Test fun healthSnapshotReflectsSemanticAdmissionOnly() {
        val valid = NavigationCalibrationObservation(
            NavigationPositionConfidence.High,
            NavigationFusionMode.DirectObservation,
            4.0,
            1L,
        )
        val available = NavigationObservationAdmission.healthSnapshot(
            NavigationDeviceCapabilities(true, true, true),
            valid,
        )
        val unavailable = NavigationObservationAdmission.healthSnapshot(
            NavigationDeviceCapabilities(false, true, true),
            valid.copy(horizontalAccuracyM = 101.0),
        )

        assertTrue(available.calibrationAvailable)
        assertTrue(available.directFreshObservationAvailable)
        assertFalse(unavailable.calibrationAvailable)
        assertFalse(unavailable.directFreshObservationAvailable)
    }
}
