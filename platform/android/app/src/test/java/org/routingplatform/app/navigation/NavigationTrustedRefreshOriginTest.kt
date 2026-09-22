package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrustedRefreshOriginTest {
    private val point = RoutePoint(48.54, 12.15)

    @Test
    fun highConfidenceDirectObservationIsAdmittedExactly() {
        assertEquals(
            point,
            NavigationTrustedRefreshOrigin.fromTelemetry(
                telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DirectObservation, point)
            )
        )
    }

    @Test
    fun deadReckoningNeverSuppliesRefreshCoordinates() {
        assertNull(
            NavigationTrustedRefreshOrigin.fromTelemetry(
                telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DeadReckoning, point)
            )
        )
    }

    @Test
    fun mediumConfidenceDirectObservationIsRejected() {
        assertNull(
            NavigationTrustedRefreshOrigin.fromTelemetry(
                telemetry(NavigationPositionConfidence.Medium, NavigationFusionMode.DirectObservation, point)
            )
        )
    }

    @Test
    fun missingObservedPositionFailsClosed() {
        assertNull(
            NavigationTrustedRefreshOrigin.fromTelemetry(
                telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DirectObservation, null)
            )
        )
    }

    private fun telemetry(
        confidence: NavigationPositionConfidence,
        fusionMode: NavigationFusionMode,
        observed: RoutePoint?,
    ) = NavigationRuntimeTelemetry(
        pipelineStatus = NavigationRuntimePipelineStatus.Running,
        automaticProgressActive = true,
        confidence = confidence,
        fusionMode = fusionMode,
        safetyStatus = null,
        rawGnssQuality = null,
        radioObservationCount = 0,
        unknownSatelliteCount = 0,
        acceptedProgress = null,
        lastLocationAccuracyM = 5.0,
        lastObservedPosition = observed,
        lastLocationElapsedRealtimeNanos = 1L,
    )
}
