package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPositionIntegrityMonitorTest {

    @Test
    fun goodRawFixIsNeverPromotedToHighConfidence() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        val result =
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        3.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            )

        assertTrue(
            result.accepted
        )

        val estimate =
            checkNotNull(
                result.estimate
            )

        assertEquals(
            NavigationPositionConfidence.Medium,
            estimate.confidence,
        )

        assertTrue(
            estimate.mayDriveNavigationProgress
        )
    }

    @Test
    fun moderateAccuracyIsKeptButLowConfidence() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        val result =
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        30.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            )

        assertTrue(
            result.accepted
        )

        val estimate =
            checkNotNull(
                result.estimate
            )

        assertEquals(
            NavigationPositionConfidence.Low,
            estimate.confidence,
        )

        assertFalse(
            estimate.mayDriveNavigationProgress
        )

        assertEquals(
            30.0,
            estimate
                .covariance
                .conservativeHorizontalSigmaM,
            0.0001,
        )
    }

    @Test
    fun veryPoorAccuracyIsRejected() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        val result =
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        120.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            )

        assertFalse(
            result.accepted
        )

        assertNull(
            result.estimate
        )

        assertTrue(
            result.issues.contains(
                PositionIntegrityIssue
                    .PoorHorizontalAccuracy
            )
        )
    }

    @Test
    fun staleTimestampIsRejected() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        assertTrue(
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        2_000_000_000L,
                )
            ).accepted
        )

        val stale =
            monitor.inspect(
                sample(
                    latitude =
                        47.1401,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            )

        assertFalse(
            stale.accepted
        )

        assertTrue(
            stale.issues.contains(
                PositionIntegrityIssue
                    .StaleTimestamp
            )
        )
    }

    @Test
    fun impossiblePositionJumpIsRejected() {
        val monitor =
            NavigationPositionIntegrityMonitor(
                maximumImpliedSpeedMps =
                    80.0
            )

        assertTrue(
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            ).accepted
        )

        val jumped =
            monitor.inspect(
                sample(
                    latitude =
                        47.1500,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        2_000_000_000L,
                )
            )

        assertFalse(
            jumped.accepted
        )

        assertNotNull(
            jumped.impliedSpeedMps
        )

        assertTrue(
            checkNotNull(
                jumped.impliedSpeedMps
            ) >
                80.0
        )

        assertTrue(
            jumped.issues.contains(
                PositionIntegrityIssue
                    .ImplausibleMotion
            )
        )
    }

    @Test
    fun plausibleMotionProducesVelocityEstimate() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        assertTrue(
            monitor.inspect(
                sample(
                    latitude =
                        0.0,

                    longitude =
                        0.0,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            ).accepted
        )

        val second =
            monitor.inspect(
                sample(
                    latitude =
                        0.0,

                    longitude =
                        0.0001,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        2_000_000_000L,
                )
            )

        assertTrue(
            second.accepted
        )

        val speed =
            checkNotNull(
                second.impliedSpeedMps
            )

        assertTrue(
            speed in 11.0..11.3
        )
    }

    @Test
    fun rejectedJumpDoesNotReplaceLastAcceptedSample() {
        val monitor =
            NavigationPositionIntegrityMonitor(
                maximumImpliedSpeedMps =
                    80.0
            )

        assertTrue(
            monitor.inspect(
                sample(
                    latitude =
                        0.0,

                    longitude =
                        0.0,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            ).accepted
        )

        assertFalse(
            monitor.inspect(
                sample(
                    latitude =
                        0.1,

                    longitude =
                        0.0,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        2_000_000_000L,
                )
            ).accepted
        )

        val recovered =
            monitor.inspect(
                sample(
                    latitude =
                        0.0,

                    longitude =
                        0.0001,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        3_000_000_000L,
                )
            )

        assertTrue(
            recovered.accepted
        )

        assertTrue(
            checkNotNull(
                recovered.impliedSpeedMps
            ) <
                80.0
        )
    }

    @Test
    fun unknownAccuracyRemainsLowConfidence() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        val result =
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        null,

                    elapsedRealtimeNanos =
                        1_000_000_000L,
                )
            )

        assertTrue(
            result.accepted
        )

        val estimate =
            checkNotNull(
                result.estimate
            )

        assertEquals(
            NavigationPositionConfidence.Low,
            estimate.confidence,
        )

        assertEquals(
            50.0,
            estimate
                .covariance
                .conservativeHorizontalSigmaM,
            0.0001,
        )
    }

    @Test
    fun resetStartsNewIntegrityTimeline() {
        val monitor =
            NavigationPositionIntegrityMonitor()

        assertTrue(
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        10_000L,
                )
            ).accepted
        )

        monitor.reset()

        val restarted =
            monitor.inspect(
                sample(
                    latitude =
                        47.1400,

                    longitude =
                        9.5200,

                    accuracyM =
                        5.0,

                    elapsedRealtimeNanos =
                        1_000L,
                )
            )

        assertTrue(
            restarted.accepted
        )
    }

    @Test
    fun covarianceRejectsImpossibleMatrix() {
        try {
            PositionCovariance2D(
                eastVarianceM2 =
                    1.0,

                northVarianceM2 =
                    1.0,

                eastNorthCovarianceM2 =
                    2.0,
            )

            throw AssertionError(
                "Expected IllegalArgumentException."
            )
        } catch (
            _: IllegalArgumentException
        ) {
            Unit
        }
    }

    private fun sample(
        latitude: Double,
        longitude: Double,
        accuracyM: Double?,
        elapsedRealtimeNanos: Long,
    ) =
        NavigationLocationSample(
            position =
                RoutePoint(
                    latitude =
                        latitude,

                    longitude =
                        longitude,
                ),

            horizontalAccuracyM =
                accuracyM,

            elapsedRealtimeNanos =
                elapsedRealtimeNanos,

            provider =
                "gps",
        )
}