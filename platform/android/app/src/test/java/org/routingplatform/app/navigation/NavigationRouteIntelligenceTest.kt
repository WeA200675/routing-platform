package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteIntelligenceTest {

    @Test
    fun parallelOppositeSegmentsProduceMultipleHypotheses() {
        val matcher =
            NavigationMultiHypothesisRouteMatcher()

        val result =
            matcher.match(
                route =
                    parallelRoute(),

                estimate =
                    estimate(
                        latitude =
                            0.00005,

                        longitude =
                            0.0005,

                        bearing =
                            null,

                        timestamp =
                            1_000_000_000L,
                    ),
            )

        assertTrue(
            result.hypotheses.size >=
                2
        )

        assertNotNull(
            result.ambiguityMargin
        )

        assertTrue(
            checkNotNull(
                result.ambiguityMargin
            ) <
                0.75
        )
    }

    @Test
    fun headingResolvesOppositeDirectionParallelGeometry() {
        val matcher =
            NavigationMultiHypothesisRouteMatcher()

        val result =
            matcher.match(
                route =
                    parallelRoute(),

                estimate =
                    estimate(
                        latitude =
                            0.00005,

                        longitude =
                            0.0005,

                        bearing =
                            90.0,

                        timestamp =
                            1_000_000_000L,
                    ),
            )

        val best =
            checkNotNull(
                result.best
            )

        assertEquals(
            0,
            best.anchor.shapeSegmentIndex,
        )

        assertTrue(
            checkNotNull(
                result.ambiguityMargin
            ) >
                0.75
        )
    }

    @Test
    fun safetyGateHoldsAmbiguousParallelRoadPosition() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val decision =
            gate.evaluate(
                route =
                    parallelRoute(),

                fusion =
                    fusionUpdate(
                        estimate =
                            estimate(
                                latitude =
                                    0.00005,

                                longitude =
                                    0.0005,

                                bearing =
                                    null,

                                timestamp =
                                    1_000_000_000L,
                            )
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldAmbiguous,
            decision.status,
        )

        assertFalse(
            decision.mayUpdateNativeRuntime
        )
    }

    @Test
    fun safetyGateAcceptsClearProgress() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val route =
            straightRoute()

        val first =
            gate.evaluate(
                route =
                    route,

                fusion =
                    fusionUpdate(
                        estimate =
                            estimate(
                                latitude =
                                    0.0,

                                longitude =
                                    0.0002,

                                bearing =
                                    90.0,

                                timestamp =
                                    1_000_000_000L,
                            )
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            first.status,
        )

        assertTrue(
            first.mayUpdateNativeRuntime
        )

        val second =
            gate.evaluate(
                route =
                    route,

                fusion =
                    fusionUpdate(
                        estimate =
                            estimate(
                                latitude =
                                    0.0,

                                longitude =
                                    0.00035,

                                bearing =
                                    90.0,

                                timestamp =
                                    2_000_000_000L,
                            )
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            second.status,
        )

        assertTrue(
            checkNotNull(
                second.acceptedProgress
            ).shapePosition >=
                checkNotNull(
                    first.acceptedProgress
                ).shapePosition
        )
    }

    @Test
    fun safetyGateRejectsImplausibleForwardJump() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val route =
            straightRoute()

        val first =
            gate.evaluate(
                route =
                    route,

                fusion =
                    fusionUpdate(
                        estimate =
                            estimate(
                                latitude =
                                    0.0,

                                longitude =
                                    0.0001,

                                bearing =
                                    90.0,

                                timestamp =
                                    1_000_000_000L,

                                speedMps =
                                    10.0,
                            )
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            first.status,
        )

        val jump =
            gate.evaluate(
                route =
                    route,

                fusion =
                    fusionUpdate(
                        estimate =
                            estimate(
                                latitude =
                                    0.0,

                                longitude =
                                    0.0018,

                                bearing =
                                    90.0,

                                timestamp =
                                    2_000_000_000L,

                                speedMps =
                                    10.0,
                            )
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldForwardJump,
            jump.status,
        )

        assertFalse(
            jump.mayUpdateNativeRuntime
        )
    }

    @Test
    fun lowConfidenceCannotMoveNativeProgress() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val lowEstimate =
            estimate(
                latitude =
                    0.0,

                longitude =
                    0.0002,

                bearing =
                    90.0,

                timestamp =
                    1_000_000_000L,
            ).copy(
                confidence =
                    NavigationPositionConfidence.Low
            )

        val decision =
            gate.evaluate(
                route =
                    straightRoute(),

                fusion =
                    fusionUpdate(
                        estimate =
                            lowEstimate
                    ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldLowConfidence,
            decision.status,
        )

        assertFalse(
            decision.mayUpdateNativeRuntime
        )
    }

    @Test
    fun exitLookaheadFindsUpcomingExitByAlongRouteDistance() {
        val route =
            straightRoute()

        val engine =
            NavigationExitLookaheadEngine(
                maximumLookaheadM =
                    500.0
            )

        val exit =
            NavigationRouteEvent(
                anchor =
                    RouteProgressAnchor(
                        shapeSegmentIndex =
                            1,

                        segmentFraction =
                            0.5,
                    ),

                type =
                    ManeuverType.Exit,

                instruction =
                    "Ausfahrt nehmen",

                roadName =
                    null,

                roadRef =
                    "A1",

                directionLabel =
                    "Zentrum",
            )

        val result =
            engine.criticalEventAhead(
                route =
                    route,

                currentProgress =
                    RouteProgressAnchor(
                        shapeSegmentIndex =
                            0,

                        segmentFraction =
                            0.5,
                    ),

                events =
                    listOf(
                        exit
                    ),
            )

        assertNotNull(
            result
        )

        assertTrue(
            checkNotNull(
                result
            ).distanceAheadM >
                0.0
        )

        assertEquals(
            ManeuverType.Exit,
            result.event.type,
        )
    }

    @Test
    fun startOrientationShowsRouteAndTrueOppositeDirection() {
        val engine =
            NavigationStartOrientationEngine()

        val info =
            engine.calculate(
                route =
                    straightRoute(),

                roadContext =
                    NavigationStartRoadContext(
                        routeRoadName =
                            "A1",

                        routeRoadRef =
                            "A1",

                        routeDirectionLabel =
                            "Nürnberg",

                        oppositeRoadName =
                            "A1",

                        oppositeRoadRef =
                            "A1",

                        oppositeDirectionLabel =
                            "München",
                    ),
            )

        assertEquals(
            "E",
            info.routeCardinalDirection,
        )

        assertEquals(
            "W",
            info.oppositeCardinalDirection,
        )

        assertEquals(
            "Nürnberg",
            info.routeDirectionLabel,
        )

        assertEquals(
            "München",
            info.oppositeDirectionLabel,
        )

        assertEquals(
            NavigationStartOrientationConfidence.High,
            info.confidence,
        )
    }

    @Test
    fun startOrientationDoesNotInventDestinationWithoutRoadMetadata() {
        val engine =
            NavigationStartOrientationEngine()

        val info =
            engine.calculate(
                route =
                    straightRoute()
            )

        assertEquals(
            "E",
            info.routeCardinalDirection,
        )

        assertEquals(
            "W",
            info.oppositeCardinalDirection,
        )

        assertEquals(
            null,
            info.routeDirectionLabel,
        )

        assertEquals(
            null,
            info.oppositeDirectionLabel,
        )

        assertEquals(
            NavigationStartOrientationConfidence.Medium,
            info.confidence,
        )
    }

    @Test
    fun startOrientationDisappearsAfterInitialDrivingPhase() {
        val controller =
            NavigationStartOrientationVisibilityController()

        assertTrue(
            controller.shouldShow(
                state =
                    NavigationSessionState.Preview,

                distanceFromStartM =
                    0.0,

                navigationElapsedNanos =
                    0L,
            )
        )

        assertTrue(
            controller.shouldShow(
                state =
                    NavigationSessionState.Navigating,

                distanceFromStartM =
                    50.0,

                navigationElapsedNanos =
                    10_000_000_000L,
            )
        )

        assertFalse(
            controller.shouldShow(
                state =
                    NavigationSessionState.Navigating,

                distanceFromStartM =
                    160.0,

                navigationElapsedNanos =
                    15_000_000_000L,
            )
        )

        assertFalse(
            controller.shouldShow(
                state =
                    NavigationSessionState.Navigating,

                distanceFromStartM =
                    20.0,

                navigationElapsedNanos =
                    31_000_000_000L,
            )
        )
    }

    private fun parallelRoute():
        List<RoutePoint> =
        listOf(
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.0,
            ),
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.001,
            ),
            RoutePoint(
                latitude =
                    0.0001,

                longitude =
                    0.001,
            ),
            RoutePoint(
                latitude =
                    0.0001,

                longitude =
                    0.0,
            ),
        )

    private fun straightRoute():
        List<RoutePoint> =
        listOf(
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.0,
            ),
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.001,
            ),
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.002,
            ),
        )

    private fun estimate(
        latitude: Double,
        longitude: Double,
        bearing: Double?,
        timestamp: Long,
        speedMps: Double = 10.0,
    ) =
        NavigationPositionEstimate(
            position =
                RoutePoint(
                    latitude =
                        latitude,

                    longitude =
                        longitude,
                ),

            horizontalVelocityMps =
                speedMps,

            bearingDegrees =
                bearing,

            covariance =
                PositionCovariance2D(
                    eastVarianceM2 =
                        25.0,

                    northVarianceM2 =
                        25.0,
                ),

            confidence =
                NavigationPositionConfidence.Medium,

            elapsedRealtimeNanos =
                timestamp,
        )

    private fun fusionUpdate(
        estimate:
            NavigationPositionEstimate,
    ) =
        NavigationPositionFusionUpdate(
            estimate =
                estimate,

            mode =
                NavigationFusionMode.DirectObservation,

            stabilizedConfidence =
                estimate.confidence,

            motionAgreement =
                NavigationMotionAgreement.Consistent,

            integrityIssues =
                emptySet(),

            rawGnssQuality =
                NavigationRawGnssQuality.Strong,

            radioObservationCount =
                0,

            unknownSatelliteCount =
                0,
        )
}