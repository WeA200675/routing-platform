package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationLocationProgressControllerTest {

    private val route =
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

    @Test
    fun acceptsForwardOnRouteSample() {
        val controller =
            controller()

        val projection =
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0005,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )

        assertNotNull(
            projection
        )

        val accepted =
            checkNotNull(
                projection
            )

        assertEquals(
            0,
            accepted.shapeSegmentIndex,
        )

        assertEquals(
            0.5,
            accepted.segmentFraction,
            0.001,
        )
    }

    @Test
    fun rejectsPoorAccuracy() {
        val controller =
            controller()

        val projection =
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0005,

                        accuracyM =
                            100.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )

        assertNull(
            projection
        )
    }

    @Test
    fun rejectsOffRouteSample() {
        val controller =
            controller()

        val projection =
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    NavigationLocationSample(
                        position =
                            RoutePoint(
                                latitude =
                                    0.01,

                                longitude =
                                    0.0005,
                            ),

                        horizontalAccuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,

                        provider =
                            "gps",
                    ),
            )

        assertNull(
            projection
        )
    }

    @Test
    fun neverMovesBehindNativeSnapshot() {
        val controller =
            controller()

        val projection =
            controller.project(
                snapshot =
                    snapshot(
                        shapeSegmentIndex =
                            1,

                        segmentFraction =
                            0.5,
                    ),

                sample =
                    sample(
                        longitude =
                            0.0014,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )

        assertNull(
            projection
        )
    }

    @Test
    fun rejectsStaleLocationSample() {
        val controller =
            controller()

        val first =
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0005,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            200L,
                    ),
            )

        assertNotNull(
            first
        )

        val stale =
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0015,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )

        assertNull(
            stale
        )
    }

    @Test
    fun ignoresLocationsOutsideNavigatingState() {
        val controller =
            controller()

        val projection =
            controller.project(
                snapshot =
                    snapshot(
                        state =
                            NavigationSessionState.Preview,
                    ),

                sample =
                    sample(
                        longitude =
                            0.0005,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )

        assertNull(
            projection
        )
    }

    @Test
    fun resetStartsNewLocationTimeline() {
        val controller =
            controller()

        assertNotNull(
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0005,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            200L,
                    ),
            )
        )

        controller.reset()

        assertNotNull(
            controller.project(
                snapshot =
                    snapshot(),

                sample =
                    sample(
                        longitude =
                            0.0006,

                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            100L,
                    ),
            )
        )
    }

    private fun controller() =
        NavigationLocationProgressController(
            maxHorizontalAccuracyM =
                50.0,

            maxDistanceToRouteM =
                50.0,
        )

    private fun sample(
        longitude: Double,
        accuracyM: Double?,
        elapsedRealtimeNanos: Long,
    ) =
        NavigationLocationSample(
            position =
                RoutePoint(
                    latitude =
                        0.0,

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

    private fun snapshot(
        state:
            NavigationSessionState =
            NavigationSessionState.Navigating,

        shapeSegmentIndex: Int = 0,
        segmentFraction: Double = 0.0,
    ) =
        NavigationUiSnapshot(
            sessionId =
                "session:test",

            state =
                state,

            routeId =
                "route:test",

            routeDistanceM =
                3000.0,

            routeDurationS =
                600.0,

            geometry =
                route,

            shapeSegmentIndex =
                shapeSegmentIndex,

            segmentFraction =
                segmentFraction,

            progressFraction =
                0.0,

            remainingDistanceM =
                3000.0,

            remainingDurationS =
                600.0,

            currentManeuver =
                null,

            nextManeuver =
                null,

            distanceToCurrentManeuverEndM =
                0.0,

            arrived =
                state ==
                    NavigationSessionState.Arrived,
        )
}