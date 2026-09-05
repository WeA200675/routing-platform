package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProgressCoordinatorTest {

    @Test
    fun nativeArbiterRequiresStrictForwardProgress() {
        val arbiter =
            NavigationNativeProgressArbiter()

        arbiter.seed(
            RouteProgressAnchor(
                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.5,
            )
        )

        assertTrue(
            !arbiter.shouldForward(
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        0,

                    segmentFraction =
                        0.5,
                )
            )
        )

        assertTrue(
            !arbiter.shouldForward(
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        0,

                    segmentFraction =
                        0.4,
                )
            )
        )

        assertTrue(
            arbiter.shouldForward(
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        0,

                    segmentFraction =
                        0.6,
                )
            )
        )
    }

    @Test
    fun coordinatorForwardsOnlySafetyApprovedMonotonicProgress() {
        val route =
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

        val bridge =
            RecordingBridge(
                route
            )

        val coordinator =
            NavigationProgressCoordinator(
                bridge =
                    bridge
            )

        coordinator.start(
            RouteProgressAnchor(
                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.0,
            )
        )

        repeat(
            8
        ) {
                index ->

            val timestamp =
                (
                    index.toLong() +
                        1L
                ) *
                    1_000_000_000L

            coordinator.ingest(
                route =
                    route,

                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    0.0,

                                longitude =
                                    index *
                                        0.0001,
                            ),

                        horizontalAccuracyM =
                            4.0,

                        elapsedRealtimeNanos =
                            timestamp,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            timestamp
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            timestamp
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            timestamp
                    ),
            )
        }

        assertTrue(
            bridge
                .progressUpdates
                .isNotEmpty()
        )

        bridge
            .progressUpdates
            .zipWithNext()
            .forEach {
                    pair ->

                assertTrue(
                    pair.second.shapePosition >
                        pair.first.shapePosition
                )
            }

        assertNotNull(
            coordinator
                .currentNativeProgress()
        )
    }

    @Test
    fun lowConfidenceFirstFixCannotImmediatelyReachNativeRuntime() {
        val route =
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
            )

        val bridge =
            RecordingBridge(
                route
            )

        val coordinator =
            NavigationProgressCoordinator(
                bridge =
                    bridge
            )

        coordinator.start(
            RouteProgressAnchor(
                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.0,
            )
        )

        val timestamp =
            1_000_000_000L

        val result =
            coordinator.ingest(
                route =
                    route,

                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    0.0,

                                longitude =
                                    0.0002,
                            ),

                        horizontalAccuracyM =
                            4.0,

                        elapsedRealtimeNanos =
                            timestamp,

                        provider =
                            "gps",
                    ),

                sensors =
                    sensorSnapshot(
                        timestamp =
                            timestamp
                    ),

                environment =
                    strongEnvironment(
                        timestamp =
                            timestamp
                    ),

                rawGnss =
                    strongRawGnss(
                        timestamp =
                            timestamp
                    ),
            )

        assertEquals(
            NavigationPositionConfidence.Low,
            result
                .fusionUpdate
                .stabilizedConfidence,
        )

        assertTrue(
            !result.nativeForwarded
        )

        assertTrue(
            bridge
                .progressUpdates
                .isEmpty()
        )
    }

    private fun sensorSnapshot(
        timestamp: Long,
    ) =
        NavigationSensorEvidenceSnapshot(
            gnss =
                null,

            acceleration =
                NavigationVector3Sample(
                    x =
                        0.0,

                    y =
                        0.0,

                    z =
                        9.81,

                    elapsedRealtimeNanos =
                        timestamp,
                ),

            gyroscope =
                NavigationVector3Sample(
                    x =
                        0.0,

                    y =
                        0.0,

                    z =
                        0.0,

                    elapsedRealtimeNanos =
                        timestamp,
                ),

            bearing =
                NavigationBearingSample(
                    bearingDegrees =
                        90.0,

                    elapsedRealtimeNanos =
                        timestamp,
                ),
        )

    private fun strongEnvironment(
        timestamp: Long,
    ) =
        NavigationEnvironmentEvidenceSnapshot(
            observedSatellites =
                (1..12).map {
                        index ->

                    NavigationObservedSatellite(
                        key =
                            NavigationSatelliteKey(
                                constellation =
                                    if (
                                        index <=
                                            6
                                    ) {
                                        NavigationGnssConstellation.Gps
                                    } else {
                                        NavigationGnssConstellation.Galileo
                                    },

                                svid =
                                    index,
                            ),

                        cn0DbHz =
                            31.0,

                        azimuthDegrees =
                            (
                                index *
                                    25.0
                            ) %
                                360.0,

                        elevationDegrees =
                            45.0,

                        usedInFix =
                            index <=
                                9,

                        carrierFrequencyHz =
                            1_575_420_000.0,

                        hasAlmanacData =
                            true,

                        hasEphemerisData =
                            true,

                        elapsedRealtimeNanos =
                            timestamp,
                    )
                },

            radioObservations =
                emptyList(),

            satelliteCatalogVersion =
                "test",

            unknownSatelliteKeys =
                emptySet(),

            capturedAtElapsedRealtimeNanos =
                timestamp,
        )

    private fun strongRawGnss(
        timestamp: Long,
    ) =
        NavigationRawGnssSnapshot(
            clock =
                NavigationRawGnssClockEvidence(
                    biasNanos =
                        0.0,

                    biasUncertaintyNanos =
                        10.0,
                ),

            measurements =
                (1..12).map {
                        index ->

                    NavigationRawGnssMeasurement(
                        satellite =
                            NavigationSatelliteKey(
                                constellation =
                                    if (
                                        index <=
                                            6
                                    ) {
                                        NavigationGnssConstellation.Gps
                                    } else {
                                        NavigationGnssConstellation.Galileo
                                    },

                                svid =
                                    index,
                            ),

                        cn0DbHz =
                            32.0,

                        pseudorangeRateMps =
                            -125.0,

                        pseudorangeRateUncertaintyMps =
                            0.2,

                        carrierFrequencyHz =
                            if (
                                index %
                                    2 ==
                                    0
                            ) {
                                1_575_420_000.0
                            } else {
                                1_176_450_000.0
                            },

                        state =
                            1,

                        elapsedRealtimeNanos =
                            timestamp,
                    )
                },

            elapsedRealtimeNanos =
                timestamp,
        )

    private class RecordingBridge(
        private val route:
            List<RoutePoint>,
    ) :
        NavigationCoreBridge {

        val progressUpdates =
            mutableListOf<
                RouteProgressAnchor
            >()

        private var snapshot =
            initialSnapshot()

        override fun currentSnapshot():
            NavigationUiSnapshot =
            snapshot

        override fun startNavigation():
            NavigationUiSnapshot {

            snapshot =
                snapshot.copy(
                    state =
                        NavigationSessionState.Navigating
                )

            return snapshot
        }

        override fun updateProgress(
            shapeSegmentIndex:
                Int,

            segmentFraction:
                Double,
        ): NavigationUiSnapshot {

            val anchor =
                RouteProgressAnchor(
                    shapeSegmentIndex =
                        shapeSegmentIndex,

                    segmentFraction =
                        segmentFraction,
                )

            progressUpdates.add(
                anchor
            )

            val progress =
                (
                    anchor.shapePosition /
                        route.lastIndex
                ).coerceIn(
                    0.0,
                    1.0,
                )

            snapshot =
                snapshot.copy(
                    state =
                        NavigationSessionState.Navigating,

                    shapeSegmentIndex =
                        shapeSegmentIndex,

                    segmentFraction =
                        segmentFraction,

                    progressFraction =
                        progress,

                    remainingDistanceM =
                        250.0 *
                            (
                                1.0 -
                                    progress
                            ),

                    remainingDurationS =
                        30.0 *
                            (
                                1.0 -
                                    progress
                            ),
                )

            return snapshot
        }

        private fun initialSnapshot() =
            NavigationUiSnapshot(
                sessionId =
                    "test-session",

                state =
                    NavigationSessionState.Preview,

                routeId =
                    "test-route",

                routeDistanceM =
                    250.0,

                routeDurationS =
                    30.0,

                geometry =
                    route,

                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.0,

                progressFraction =
                    0.0,

                remainingDistanceM =
                    250.0,

                remainingDurationS =
                    30.0,

                currentManeuver =
                    null,

                nextManeuver =
                    null,

                distanceToCurrentManeuverEndM =
                    250.0,

                arrived =
                    false,
            )
    }
}