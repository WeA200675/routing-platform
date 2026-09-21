package org.routingplatform.app.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRerouteDecisionEngineTest {

    @Test
    fun sustainedTrustedOffRouteEvidenceRequestsReplacement() {
        val engine =
            NavigationRerouteDecisionEngine(
                NavigationReroutePolicy(
                    minimumEvidenceDurationNanos =
                        10_000_000_000L,

                    minimumConsecutiveSamples =
                        6,

                    minimumAttemptIntervalNanos =
                        30_000_000_000L,
                )
            )

        val times =
            listOf(
                0L,
                2_000_000_000L,
                4_000_000_000L,
                6_000_000_000L,
                8_000_000_000L,
                10_000_000_000L,
            )

        times
            .dropLast(
                1
            )
            .forEach {
                    timestamp ->

                assertTrue(
                    engine.observe(
                        telemetry(
                            timestamp =
                                timestamp,

                            status =
                                NavigationRouteProgressSafetyStatus.HeldOffRoute,

                            confidence =
                                NavigationPositionConfidence.Medium,
                        )
                    ) is
                        NavigationRerouteDecision.Hold
                )
            }

        assertTrue(
            engine.observe(
                telemetry(
                    timestamp =
                        times.last(),

                    status =
                        NavigationRouteProgressSafetyStatus.HeldOffRoute,

                    confidence =
                        NavigationPositionConfidence.Medium,
                )
            ) is
                NavigationRerouteDecision.RequestReplacement
        )
    }

    @Test
    fun lowConfidenceNeverRequestsReplacement() {
        val engine =
            NavigationRerouteDecisionEngine()

        repeat(
            20
        ) {
                index ->

            assertTrue(
                engine.observe(
                    telemetry(
                        timestamp =
                            index.toLong() *
                                2_000_000_000L,

                        status =
                            NavigationRouteProgressSafetyStatus.HeldOffRoute,

                        confidence =
                            NavigationPositionConfidence.Low,
                    )
                ) is
                    NavigationRerouteDecision.Hold
            )
        }
    }

    @Test
    fun ambiguityNeverBecomesRerouteEvidence() {
        val engine =
            NavigationRerouteDecisionEngine()

        repeat(
            20
        ) {
                index ->

            assertTrue(
                engine.observe(
                    telemetry(
                        timestamp =
                            index.toLong() *
                                2_000_000_000L,

                        status =
                            NavigationRouteProgressSafetyStatus.HeldAmbiguous,

                        confidence =
                            NavigationPositionConfidence.High,
                    )
                ) is
                    NavigationRerouteDecision.Hold
            )
        }
    }

    @Test
    fun deadReckoningNeverRequestsReplacement() {
        val engine =
            NavigationRerouteDecisionEngine()

        repeat(
            20
        ) {
                index ->

            val source =
                telemetry(
                    timestamp =
                        index.toLong() *
                            2_000_000_000L,

                    status =
                        NavigationRouteProgressSafetyStatus.HeldOffRoute,

                    confidence =
                        NavigationPositionConfidence.Medium,
                )

            assertTrue(
                engine.observe(
                    source.copy(
                        fusionMode =
                            NavigationFusionMode.DeadReckoning
                    )
                ) is
                    NavigationRerouteDecision.Hold
            )
        }
    }

    @Test
    fun attemptIntervalPreventsRerouteOscillation() {
        val engine =
            NavigationRerouteDecisionEngine(
                NavigationReroutePolicy(
                    minimumEvidenceDurationNanos =
                        2_000_000_000L,

                    minimumConsecutiveSamples =
                        3,

                    minimumAttemptIntervalNanos =
                        30_000_000_000L,
                )
            )

        fun observeSeries(start: Long): NavigationRerouteDecision {
            var decision: NavigationRerouteDecision =
                NavigationRerouteDecision.Hold

            repeat(3) { index ->
                decision =
                    engine.observe(
                        telemetry(
                            timestamp =
                                start +
                                    index *
                                    1_000_000_000L,

                            status =
                                NavigationRouteProgressSafetyStatus.HeldOffRoute,

                            confidence =
                                NavigationPositionConfidence.High,
                        )
                    )
            }

            return decision
        }

        assertTrue(
            observeSeries(0L) is
                NavigationRerouteDecision.RequestReplacement
        )

        assertTrue(
            observeSeries(10_000_000_000L) is
                NavigationRerouteDecision.Hold
        )

        // The suppressed candidate remains accumulated. Exactly when the
        // attempt interval expires, the first fresh eligible observation may
        // request a replacement; subsequent observations start a new candidate.
        assertTrue(
            engine.observe(
                telemetry(
                    timestamp =
                        32_000_000_000L,

                    status =
                        NavigationRouteProgressSafetyStatus.HeldOffRoute,

                    confidence =
                        NavigationPositionConfidence.High,
                )
            ) is
                NavigationRerouteDecision.RequestReplacement
        )

        assertTrue(
            engine.observe(
                telemetry(
                    timestamp =
                        33_000_000_000L,

                    status =
                        NavigationRouteProgressSafetyStatus.HeldOffRoute,

                    confidence =
                        NavigationPositionConfidence.High,
                )
            ) is
                NavigationRerouteDecision.Hold
        )
    }

    @Test
    fun staleOrDuplicateTimestampResetsCandidateEvidence() {
        val engine =
            NavigationRerouteDecisionEngine(
                NavigationReroutePolicy(
                    minimumEvidenceDurationNanos =
                        2_000_000_000L,

                    minimumConsecutiveSamples =
                        3,
                )
            )

        val first =
            telemetry(
                timestamp =
                    1_000_000_000L,

                status =
                    NavigationRouteProgressSafetyStatus.HeldOffRoute,

                confidence =
                    NavigationPositionConfidence.High,
            )

        assertTrue(
            engine.observe(first) is
                NavigationRerouteDecision.Hold
        )

        assertTrue(
            engine.observe(first) is
                NavigationRerouteDecision.Hold
        )

        assertTrue(
            engine.observe(
                first.copy(
                    lastLocationElapsedRealtimeNanos =
                        2_000_000_000L
                )
            ) is
                NavigationRerouteDecision.Hold
        )

        assertTrue(
            engine.observe(
                first.copy(
                    lastLocationElapsedRealtimeNanos =
                        3_000_000_000L
                )
            ) is
                NavigationRerouteDecision.Hold
        )
    }

    private fun telemetry(
        timestamp: Long,
        status:
            NavigationRouteProgressSafetyStatus,
        confidence:
            NavigationPositionConfidence,
    ): NavigationRuntimeTelemetry =
        NavigationRuntimeTelemetry(
            pipelineStatus =
                NavigationRuntimePipelineStatus.SafetyHold,

            automaticProgressActive =
                true,

            confidence =
                confidence,

            fusionMode =
                NavigationFusionMode.DirectObservation,

            safetyStatus =
                status,

            rawGnssQuality =
                null,

            radioObservationCount =
                0,

            unknownSatelliteCount =
                0,

            acceptedProgress =
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        0,

                    segmentFraction =
                        0.0,
                ),

            lastLocationAccuracyM =
                5.0,

            lastObservedPosition =
                RoutePoint(
                    latitude =
                        47.1,

                    longitude =
                        9.5,
                ),

            lastLocationElapsedRealtimeNanos =
                timestamp,
        )
}