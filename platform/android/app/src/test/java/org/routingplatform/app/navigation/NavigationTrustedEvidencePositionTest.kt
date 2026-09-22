package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrustedEvidencePositionTest {
    private fun telemetry(observedAt: Long) = NavigationRuntimeTelemetry.stopped().copy(
        confidence = NavigationPositionConfidence.High,
        fusionMode = NavigationFusionMode.DirectObservation,
        lastObservedPosition = RoutePoint(48.47, 11.94),
        lastLocationElapsedRealtimeNanos = observedAt,
        lastLocationAccuracyM = 4.5,
    )

    @Test fun exposesOnlyCurrentTrustedVehicleObservation() {
        val p = NavigationTrustedEvidencePosition.fromTelemetry(telemetry(100), 101)!!
        assertEquals(48.47, p.latitude, 0.0)
        assertEquals(4.5, p.accuracyM, 0.0)
    }

    @Test fun staleObservationHasNoEvidenceCoordinate() {
        assertNull(NavigationTrustedEvidencePosition.fromTelemetry(
            telemetry(100), 100 + NavigationTrustedRefreshOrigin.MAX_AGE_NANOS + 1
        ))
    }

    @Test fun futureObservationHasNoEvidenceCoordinate() {
        assertNull(NavigationTrustedEvidencePosition.fromTelemetry(telemetry(101), 100))
    }

    @Test fun deadReckoningHasNoEvidenceCoordinate() {
        val t = telemetry(100).copy(fusionMode = NavigationFusionMode.DeadReckoning)
        assertNull(NavigationTrustedEvidencePosition.fromTelemetry(t, 101))
    }
}
