package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationTrustedRefreshOriginTest {
    private val point = RoutePoint(48.54, 12.15)

    @Test
    fun highConfidenceDirectObservationIsAdmittedExactly() {
        val observation = NavigationTrustedRefreshOrigin.fromTelemetry(
            telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DirectObservation, point)
        )
        assertEquals(point, NavigationTrustedRefreshOrigin.current(observation, 1L))
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

    @Test
    fun staleObservationFailsClosed() {
        val observation = NavigationTrustedRefreshOrigin.fromTelemetry(
            telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DirectObservation, point)
        )
        assertNull(
            NavigationTrustedRefreshOrigin.current(
                observation,
                1L + NavigationTrustedRefreshOrigin.MAX_AGE_NANOS + 1L,
            )
        )
    }

    @Test
    fun futureObservationFailsClosed() {
        val observation = NavigationTrustedRefreshOrigin.fromTelemetry(
            telemetry(NavigationPositionConfidence.High, NavigationFusionMode.DirectObservation, point)
        )
        assertNull(NavigationTrustedRefreshOrigin.current(observation, 0L))
    }

    @Test
    fun missingMonotonicTimestampFailsClosed() {
        assertNull(
            NavigationTrustedRefreshOrigin.fromTelemetry(
                telemetry(
                    NavigationPositionConfidence.High,
                    NavigationFusionMode.DirectObservation,
                    point,
                    elapsedRealtimeNanos = null,
                )
            )
        )
    }

    private fun telemetry(
        confidence: NavigationPositionConfidence,
        fusionMode: NavigationFusionMode,
        observed: RoutePoint?,
        elapsedRealtimeNanos: Long? = 1L,
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
        lastLocationElapsedRealtimeNanos = elapsedRealtimeNanos,
    )
}
