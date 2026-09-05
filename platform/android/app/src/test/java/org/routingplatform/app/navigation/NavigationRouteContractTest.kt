package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRouteContractTest {

    @Test
    fun nativeCodecUsesVersionedMagicAndPreservesMetadataModel() {
        val route =
            testRoute()

        val payload =
            NavigationRouteNativeCodec
                .encode(
                    route
                )

        assertEquals(
            "NRT1",
            String(
                payload.copyOfRange(
                    0,
                    4,
                ),
                Charsets.US_ASCII,
            )
        )

        val maneuver =
            route.maneuvers[
                1
            ].toNavigationManeuver()

        assertEquals(
            listOf(
                "Landstrasse"
            ),
            maneuver.streetNames
        )

        assertEquals(
            1,
            maneuver.beginShapeIndex
        )

        assertEquals(
            2,
            maneuver.endShapeIndex
        )

        assertEquals(
            7,
            maneuver.engineType
        )

        assertTrue(
            payload.size >
                100
        )
    }

    @Test
    fun routeContractRejectsManeuverBeyondGeometry() {
        var rejected =
            false

        try {
            NavigationRouteContract(
                routeId =
                    "invalid",

                family =
                    NavigationRouteFamily.ProfileOptimal,

                distanceM =
                    100.0,

                durationS =
                    10.0,

                geometry =
                    listOf(
                        RoutePoint(
                            47.0,
                            9.0,
                        ),
                        RoutePoint(
                            47.001,
                            9.001,
                        ),
                    ),

                maneuvers =
                    listOf(
                        NavigationRouteContractManeuver(
                            type =
                                ManeuverType.Arrive,

                            instruction =
                                "Arrive",

                            streetNames =
                                emptyList(),

                            distanceM =
                                0.0,

                            durationS =
                                0.0,

                            beginShapeIndex =
                                2,

                            endShapeIndex =
                                2,

                            bearingBeforeDeg =
                                null,

                            bearingAfterDeg =
                                null,

                            engineType =
                                null,
                        )
                    ),

                engineName =
                    "fixture",

                engineVersion =
                    "1",

                segmentDataStatus =
                    NavigationRouteSegmentDataStatus.Unspecified,

                diagnostics =
                    emptyList(),
            )
        } catch (
            _: IllegalArgumentException
        ) {
            rejected =
                true
        }

        assertTrue(
            rejected
        )
    }

    private fun testRoute():
        NavigationRouteContract =
        NavigationRouteContract(
            routeId =
                "valhalla-test",

            family =
                NavigationRouteFamily.ProfileOptimal,

            distanceM =
                300.0,

            durationS =
                30.0,

            geometry =
                listOf(
                    RoutePoint(
                        47.0,
                        9.0,
                    ),
                    RoutePoint(
                        47.001,
                        9.001,
                    ),
                    RoutePoint(
                        47.002,
                        9.002,
                    ),
                ),

            maneuvers =
                listOf(
                    NavigationRouteContractManeuver(
                        type =
                            ManeuverType.Start,

                        instruction =
                            "Start",

                        streetNames =
                            emptyList(),

                        distanceM =
                            0.0,

                        durationS =
                            0.0,

                        beginShapeIndex =
                            0,

                        endShapeIndex =
                            0,

                        bearingBeforeDeg =
                            null,

                        bearingAfterDeg =
                            30,

                        engineType =
                            1,
                    ),

                    NavigationRouteContractManeuver(
                        type =
                            ManeuverType.TurnRight,

                        instruction =
                            "Turn right",

                        streetNames =
                            listOf(
                                "Landstrasse"
                            ),

                        distanceM =
                            300.0,

                        durationS =
                            30.0,

                        beginShapeIndex =
                            1,

                        endShapeIndex =
                            2,

                        bearingBeforeDeg =
                            30,

                        bearingAfterDeg =
                            90,

                        engineType =
                            7,
                    ),
                ),

            engineName =
                "valhalla",

            engineVersion =
                "3.8.3",

            segmentDataStatus =
                NavigationRouteSegmentDataStatus.Complete,

            diagnostics =
                emptyList(),
        )
}