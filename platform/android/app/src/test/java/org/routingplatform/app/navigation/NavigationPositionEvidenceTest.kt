package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPositionEvidenceTest {

    @Test
    fun strongGnssRequiresGoodSatelliteEvidence() {
        val evidence =
            NavigationGnssEvidence(
                visibleSatelliteCount =
                    12,

                usedInFixSatelliteCount =
                    9,

                meanCn0DbHz =
                    31.0,

                maxCn0DbHz =
                    42.0,
            )

        assertEquals(
            NavigationGnssQuality.Strong,
            evidence.quality,
        )
    }

    @Test
    fun observationBufferRejectsOldSamples() {
        val buffer =
            NavigationObservationBuffer(
                capacity =
                    4
            )

        assertTrue(
            buffer.add(
                observation(
                    timestamp =
                        100L
                )
            )
        )

        assertFalse(
            buffer.add(
                observation(
                    timestamp =
                        100L
                )
            )
        )

        assertFalse(
            buffer.add(
                observation(
                    timestamp =
                        99L
                )
            )
        )

        assertEquals(
            1,
            buffer.size,
        )
    }

    @Test
    fun observationBufferIsBounded() {
        val buffer =
            NavigationObservationBuffer(
                capacity =
                    2
            )

        assertTrue(
            buffer.add(
                observation(
                    timestamp =
                        1L
                )
            )
        )

        assertTrue(
            buffer.add(
                observation(
                    timestamp =
                        2L
                )
            )
        )

        assertTrue(
            buffer.add(
                observation(
                    timestamp =
                        3L
                )
            )
        )

        assertEquals(
            2,
            buffer.size,
        )

        assertEquals(
            2L,
            buffer
                .snapshot()
                .first()
                .elapsedRealtimeNanos,
        )

        assertEquals(
            3L,
            checkNotNull(
                buffer.latest()
            ).elapsedRealtimeNanos,
        )
    }

    @Test
    fun highRequiresRepeatedStrongAndMotionConsistentEvidence() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        assertEquals(
            NavigationPositionConfidence.Low,
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        1L
                )
            ),
        )

        assertEquals(
            NavigationPositionConfidence.Medium,
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        2L
                )
            ),
        )

        hysteresis.observe(
            strongObservation(
                timestamp =
                    3L
            )
        )

        hysteresis.observe(
            strongObservation(
                timestamp =
                    4L
            )
        )

        assertEquals(
            NavigationPositionConfidence.High,
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        5L
                )
            ),
        )
    }

    @Test
    fun unknownMotionCanReachMediumButNeverHigh() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        repeat(
            10
        ) {
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        it.toLong() +
                            1L,

                    motionAgreement =
                        NavigationMotionAgreement.Unknown,
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.Medium,
            hysteresis.confidence,
        )
    }

    @Test
    fun oneWeakFixDoesNotDestroyHighConfidence() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        repeat(
            5
        ) {
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        it.toLong() +
                            1L
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.High,
            hysteresis.confidence,
        )

        hysteresis.observe(
            weakObservation(
                timestamp =
                    10L
            )
        )

        assertEquals(
            NavigationPositionConfidence.High,
            hysteresis.confidence,
        )

        hysteresis.observe(
            weakObservation(
                timestamp =
                    11L
            )
        )

        assertEquals(
            NavigationPositionConfidence.Medium,
            hysteresis.confidence,
        )
    }

    @Test
    fun repeatedWeakEvidenceDemotesGradually() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        repeat(
            5
        ) {
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        it.toLong() +
                            1L
                )
            )
        }

        repeat(
            2
        ) {
            hysteresis.observe(
                weakObservation(
                    timestamp =
                        10L +
                            it
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.Medium,
            hysteresis.confidence,
        )

        repeat(
            2
        ) {
            hysteresis.observe(
                weakObservation(
                    timestamp =
                        20L +
                            it
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.Low,
            hysteresis.confidence,
        )
    }

    @Test
    fun conflictingMotionCannotPromotePosition() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        repeat(
            8
        ) {
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        it.toLong() +
                            1L,

                    motionAgreement =
                        NavigationMotionAgreement.Conflicting,
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.Low,
            hysteresis.confidence,
        )
    }

    @Test
    fun missingEstimatesEventuallyBecomeLost() {
        val hysteresis =
            NavigationConfidenceHysteresis()

        repeat(
            5
        ) {
            hysteresis.observe(
                strongObservation(
                    timestamp =
                        it.toLong() +
                            1L
                )
            )
        }

        assertEquals(
            NavigationPositionConfidence.High,
            hysteresis.confidence,
        )

        hysteresis.observe(
            missingObservation(
                timestamp =
                    20L
            )
        )

        hysteresis.observe(
            missingObservation(
                timestamp =
                    21L
            )
        )

        assertEquals(
            NavigationPositionConfidence.High,
            hysteresis.confidence,
        )

        hysteresis.observe(
            missingObservation(
                timestamp =
                    22L
            )
        )

        assertEquals(
            NavigationPositionConfidence.Lost,
            hysteresis.confidence,
        )
    }

    private fun strongObservation(
        timestamp: Long,
        motionAgreement:
            NavigationMotionAgreement =
            NavigationMotionAgreement.Consistent,
    ) =
        NavigationPositionObservation(
            elapsedRealtimeNanos =
                timestamp,

            estimate =
                estimate(
                    timestamp =
                        timestamp,

                    confidence =
                        NavigationPositionConfidence.Medium,
                ),

            gnssEvidence =
                NavigationGnssEvidence(
                    visibleSatelliteCount =
                        12,

                    usedInFixSatelliteCount =
                        9,

                    meanCn0DbHz =
                        31.0,

                    maxCn0DbHz =
                        43.0,
                ),

            imuEvidence =
                NavigationImuEvidence(
                    accelerationMagnitudeMps2 =
                        9.81,

                    yawRateDegreesPerSecond =
                        0.0,

                    bearingDegrees =
                        90.0,
                ),

            motionAgreement =
                motionAgreement,
        )

    private fun weakObservation(
        timestamp: Long,
    ) =
        NavigationPositionObservation(
            elapsedRealtimeNanos =
                timestamp,

            estimate =
                estimate(
                    timestamp =
                        timestamp,

                    confidence =
                        NavigationPositionConfidence.Low,
                ),

            gnssEvidence =
                NavigationGnssEvidence(
                    visibleSatelliteCount =
                        4,

                    usedInFixSatelliteCount =
                        2,

                    meanCn0DbHz =
                        12.0,

                    maxCn0DbHz =
                        20.0,
                ),

            imuEvidence =
                null,

            motionAgreement =
                NavigationMotionAgreement.Unknown,
        )

    private fun missingObservation(
        timestamp: Long,
    ) =
        NavigationPositionObservation(
            elapsedRealtimeNanos =
                timestamp,

            estimate =
                null,

            gnssEvidence =
                null,

            imuEvidence =
                null,

            motionAgreement =
                NavigationMotionAgreement.Unknown,
        )

    private fun observation(
        timestamp: Long,
    ) =
        strongObservation(
            timestamp =
                timestamp
        )

    private fun estimate(
        timestamp: Long,
        confidence:
            NavigationPositionConfidence,
    ) =
        NavigationPositionEstimate(
            position =
                RoutePoint(
                    latitude =
                        47.14,

                    longitude =
                        9.52,
                ),

            horizontalVelocityMps =
                null,

            bearingDegrees =
                null,

            covariance =
                PositionCovariance2D(
                    eastVarianceM2 =
                        25.0,

                    northVarianceM2 =
                        25.0,
                ),

            confidence =
                confidence,

            elapsedRealtimeNanos =
                timestamp,
        )
}