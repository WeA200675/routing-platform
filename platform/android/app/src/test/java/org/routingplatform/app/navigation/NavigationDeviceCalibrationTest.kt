package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationDeviceCalibrationTest {
    @Test
    fun acceptsOnlyHighConfidenceDirectMonotonicObservations() {
        val accepted =
            NavigationDeviceCalibration.observe(
                NavigationDeviceCalibrationProfile(),
                NavigationCalibrationObservation(
                    confidence = NavigationPositionConfidence.High,
                    fusionMode = NavigationFusionMode.DirectObservation,
                    horizontalAccuracyM = 4.5,
                    elapsedRealtimeNanos = 10L,
                ),
            )

        assertEquals(1, accepted.acceptedDirectSamples)
        assertEquals(0, accepted.rejectedSamples)
        assertEquals(4.5, accepted.bestObservedAccuracyM!!, 0.0)
    }

    @Test
    fun rejectsDeadReckoningAndCannotCreateQualityEvidence() {
        val rejected =
            NavigationDeviceCalibration.observe(
                NavigationDeviceCalibrationProfile(),
                NavigationCalibrationObservation(
                    confidence = NavigationPositionConfidence.High,
                    fusionMode = NavigationFusionMode.DeadReckoning,
                    horizontalAccuracyM = 2.0,
                    elapsedRealtimeNanos = 10L,
                ),
            )

        assertEquals(0, rejected.acceptedDirectSamples)
        assertEquals(1, rejected.rejectedSamples)
        assertNull(rejected.bestObservedAccuracyM)
    }

    @Test
    fun rejectsMissingTimestampAndInvalidAccuracy() {
        val missingTime =
            NavigationDeviceCalibration.observe(
                NavigationDeviceCalibrationProfile(),
                NavigationCalibrationObservation(
                    confidence = NavigationPositionConfidence.High,
                    fusionMode = NavigationFusionMode.DirectObservation,
                    horizontalAccuracyM = 3.0,
                    elapsedRealtimeNanos = null,
                ),
            )
        val invalidAccuracy =
            NavigationDeviceCalibration.observe(
                missingTime,
                NavigationCalibrationObservation(
                    confidence = NavigationPositionConfidence.High,
                    fusionMode = NavigationFusionMode.DirectObservation,
                    horizontalAccuracyM = Double.NaN,
                    elapsedRealtimeNanos = 11L,
                ),
            )

        assertEquals(0, invalidAccuracy.acceptedDirectSamples)
        assertEquals(2, invalidAccuracy.rejectedSamples)
        assertNull(invalidAccuracy.bestObservedAccuracyM)
    }

    @Test
    fun keepsBestObservedAccuracyDeterministically() {
        var profile = NavigationDeviceCalibrationProfile()
        for (accuracy in listOf(8.0, 12.0, 5.0)) {
            profile =
                NavigationDeviceCalibration.observe(
                    profile,
                    NavigationCalibrationObservation(
                        confidence = NavigationPositionConfidence.High,
                        fusionMode = NavigationFusionMode.DirectObservation,
                        horizontalAccuracyM = accuracy,
                        elapsedRealtimeNanos = 10L,
                    ),
                )
        }

        assertEquals(3, profile.acceptedDirectSamples)
        assertEquals(5.0, profile.bestObservedAccuracyM!!, 0.0)
    }
}
