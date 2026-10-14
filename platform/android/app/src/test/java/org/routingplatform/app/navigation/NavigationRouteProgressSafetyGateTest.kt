package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteProgressSafetyGateTest {
    private val route =
        listOf(
            RoutePoint(47.0000, 9.0000),
            RoutePoint(47.0100, 9.0000),
        )

    @Test
    fun missingEstimateIsHeldAndCannotUpdateRuntime() {
        val decision =
            NavigationRouteProgressSafetyGate()
                .evaluate(route, fusion(null))

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldNoEstimate,
            decision.status,
        )
        assertNull(decision.acceptedProgress)
        assertFalse(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun lowAndLostConfidenceNeverDriveProgress() {
        listOf(
            NavigationPositionConfidence.Low,
            NavigationPositionConfidence.Lost,
        ).forEach { confidence ->
            val decision =
                NavigationRouteProgressSafetyGate()
                    .evaluate(
                        route,
                        fusion(estimate(47.0010, confidence = confidence)),
                    )

            assertEquals(
                NavigationRouteProgressSafetyStatus.HeldLowConfidence,
                decision.status,
            )
            assertFalse(decision.mayUpdateNativeRuntime)
        }
    }

    @Test
    fun trustedOnRouteEstimateIsAccepted() {
        val decision =
            NavigationRouteProgressSafetyGate()
                .evaluate(
                    route,
                    fusion(estimate(47.0010)),
                )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            decision.status,
        )
        assertNotNull(decision.acceptedProgress)
        assertTrue(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun farOffRouteEstimateIsHeld() {
        val decision =
            NavigationRouteProgressSafetyGate()
                .evaluate(
                    route,
                    fusion(estimate(47.0010, longitude = 9.0100)),
                )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldOffRoute,
            decision.status,
        )
        assertFalse(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun adjacentShapeSegmentsAtOneWaypointDoNotCreateFalseAmbiguity() {
        val denseRoute =
            (0..10).map { index ->
                RoutePoint(
                    latitude = 47.0000 + index * 0.0001,
                    longitude = 9.0000,
                )
            }

        val decision =
            NavigationRouteProgressSafetyGate()
                .evaluate(
                    denseRoute,
                    fusion(
                        estimate(
                            latitude = 47.0005,
                            timestamp = 1_000_000_000L,
                        )
                    ),
                )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            decision.status,
        )
        assertTrue(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun genuinelyRepeatedRouteLocationRemainsAmbiguous() {
        val crossing =
            listOf(
                RoutePoint(47.0000, 9.0000),
                RoutePoint(47.0010, 9.0010),
                RoutePoint(47.0020, 9.0000),
                RoutePoint(47.0010, 9.0010),
                RoutePoint(47.0030, 9.0010),
            )
        val location =
            estimate(
                latitude = 47.0010,
                longitude = 9.0010,
                timestamp = 1_000_000_000L,
            ).copy(bearingDegrees = null)

        val decision =
            NavigationRouteProgressSafetyGate()
                .evaluate(crossing, fusion(location))

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldAmbiguous,
            decision.status,
        )
        assertFalse(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun backwardJumpIsHeldAndLastAcceptedProgressIsPreserved() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val accepted =
            gate.evaluate(
                route,
                fusion(estimate(47.0060, timestamp = 1_000_000_000L)),
            )

        val held =
            gate.evaluate(
                route,
                fusion(estimate(47.0040, timestamp = 2_000_000_000L)),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            accepted.status,
        )
        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldBackward,
            held.status,
        )
        assertEquals(
            accepted.acceptedProgress,
            held.acceptedProgress,
        )
        assertFalse(held.mayUpdateNativeRuntime)
    }

    @Test
    fun implausibleForwardJumpIsHeldAndCannotAdvanceProgress() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val accepted =
            gate.evaluate(
                route,
                fusion(
                    estimate(
                        47.0010,
                        speed = 0.0,
                        timestamp = 1_000_000_000L,
                    )
                ),
            )

        val held =
            gate.evaluate(
                route,
                fusion(
                    estimate(
                        47.0060,
                        speed = 0.0,
                        timestamp = 2_000_000_000L,
                    )
                ),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldForwardJump,
            held.status,
        )
        assertEquals(
            accepted.acceptedProgress,
            held.acceptedProgress,
        )
        assertFalse(held.mayUpdateNativeRuntime)
    }

    @Test
    fun forwardProgressRequiresASecondDistinctCoherentFix() {
        val gate = NavigationRouteProgressSafetyGate()
        val routeStart = gate.evaluate(
            route,
            fusion(estimate(47.0010, timestamp = 1_000_000_000L)),
        )
        assertEquals(NavigationRouteProgressSafetyStatus.Accepted, routeStart.status)

        val firstCandidate = gate.evaluate(
            route,
            fusion(estimate(
                47.0013,
                speed = 15.0,
                timestamp = 2_000_000_000L,
            )),
        )
        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldUnconfirmedProgress,
            firstCandidate.status,
        )
        assertEquals(routeStart.acceptedProgress, firstCandidate.acceptedProgress)
        assertFalse(firstCandidate.mayUpdateNativeRuntime)

        val confirmation = gate.evaluate(
            route,
            fusion(estimate(
                47.0013,
                speed = 15.0,
                timestamp = 3_000_000_000L,
            )),
        )
        assertEquals(NavigationRouteProgressSafetyStatus.Accepted, confirmation.status)
        assertTrue(confirmation.mayUpdateNativeRuntime)
    }

    @Test
    fun inconsistentSecondFixRestartsConfirmationInsteadOfFlippingProgress() {
        val gate = NavigationRouteProgressSafetyGate()
        val routeStart = gate.evaluate(
            route,
            fusion(estimate(47.0010, timestamp = 1_000_000_000L)),
        )
        val firstCandidate = gate.evaluate(
            route,
            fusion(estimate(47.0012, speed = 15.0, timestamp = 2_000_000_000L)),
        )
        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldUnconfirmedProgress,
            firstCandidate.status,
        )

        val inconsistent = gate.evaluate(
            route,
            fusion(estimate(47.0018, speed = 15.0, timestamp = 3_000_000_000L)),
        )
        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldUnconfirmedProgress,
            inconsistent.status,
        )
        assertEquals(routeStart.acceptedProgress, inconsistent.acceptedProgress)

        val coherentFollowup = gate.evaluate(
            route,
            fusion(estimate(47.0018, speed = 15.0, timestamp = 4_000_000_000L)),
        )
        assertEquals(NavigationRouteProgressSafetyStatus.Accepted, coherentFollowup.status)
    }

    @Test
    fun conflictingMotionEvidenceAlwaysHoldsRouteProgress() {
        val gate = NavigationRouteProgressSafetyGate()
        gate.evaluate(route, fusion(estimate(47.0010, timestamp = 1_000_000_000L)))
        val decision = gate.evaluate(
            route,
            fusion(
                estimate(47.0012, timestamp = 2_000_000_000L),
                agreement = NavigationMotionAgreement.Conflicting,
            ),
        )
        assertEquals(NavigationRouteProgressSafetyStatus.HeldAmbiguous, decision.status)
        assertFalse(decision.mayUpdateNativeRuntime)
    }

    @Test
    fun seedDefinesMonotonicProgressFloor() {
        val gate =
            NavigationRouteProgressSafetyGate()

        val seeded =
            RouteProgressAnchor(
                shapeSegmentIndex = 0,
                segmentFraction = 0.60,
            )

        gate.seed(seeded)

        val decision =
            gate.evaluate(
                route,
                fusion(estimate(47.0040, timestamp = 1_000_000_000L)),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.HeldBackward,
            decision.status,
        )
        assertEquals(seeded, decision.acceptedProgress)
    }

    @Test
    fun resetClearsPreviouslyAcceptedProgress() {
        val gate =
            NavigationRouteProgressSafetyGate()

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            gate.evaluate(
                route,
                fusion(estimate(47.0030, timestamp = 1_000_000_000L)),
            ).status,
        )

        gate.reset()

        assertNull(gate.currentAcceptedProgress())

        val decision =
            gate.evaluate(
                route,
                fusion(estimate(47.0010, timestamp = 2_000_000_000L)),
            )

        assertEquals(
            NavigationRouteProgressSafetyStatus.Accepted,
            decision.status,
        )
    }

    private fun estimate(
        latitude: Double,
        longitude: Double = 9.0000,
        confidence: NavigationPositionConfidence =
            NavigationPositionConfidence.High,
        speed: Double? = 0.0,
        timestamp: Long = 1_000_000_000L,
    ) =
        NavigationPositionEstimate(
            position = RoutePoint(latitude, longitude),
            horizontalVelocityMps = speed,
            bearingDegrees = 0.0,
            covariance =
                PositionCovariance2D(
                    eastVarianceM2 = 1.0,
                    northVarianceM2 = 1.0,
                ),
            confidence = confidence,
            elapsedRealtimeNanos = timestamp,
        )

    private fun fusion(
        estimate: NavigationPositionEstimate?,
        agreement: NavigationMotionAgreement = NavigationMotionAgreement.Unknown,
    ) =
        NavigationPositionFusionUpdate(
            estimate = estimate,
            mode =
                if (estimate == null) {
                    NavigationFusionMode.Lost
                } else {
                    NavigationFusionMode.DirectObservation
                },
            stabilizedConfidence =
                estimate?.confidence
                    ?: NavigationPositionConfidence.Lost,
            motionAgreement = agreement,
            integrityIssues = emptySet(),
            rawGnssQuality = null,
            radioObservationCount = 0,
            unknownSatelliteCount = 0,
        )
}
