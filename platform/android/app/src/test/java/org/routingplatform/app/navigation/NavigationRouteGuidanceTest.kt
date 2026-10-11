package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationRouteGuidanceTest {

    @Test
    fun streetNameFeedsOnlyProvenRouteSideOfStartOrientation() {
        val context =
            buildNavigationStartRoadContext(
                listOf(
                    NavigationManeuver(
                        type =
                            ManeuverType.Continue,

                        instruction =
                            "Continue",

                        distanceM =
                            100.0,

                        durationS =
                            10.0,

                        streetNames =
                            listOf(
                                "Landstrasse"
                            ),
                    )
                )
            )

        assertNotNull(
            context
        )

        assertEquals(
            "Landstrasse",
            context?.routeRoadName
        )

        assertNull(
            context?.oppositeRoadName
        )

        assertNull(
            context?.oppositeDirectionLabel
        )
    }

    @Test
    fun finalShapePointBecomesFinalSegmentFractionOne() {
        val events =
            buildNavigationRouteEvents(
                routePointCount =
                    4,

                maneuvers =
                    listOf(
                        NavigationManeuver(
                            type =
                                ManeuverType.Arrive,

                            instruction =
                                "Arrive",

                            distanceM =
                                0.0,

                            durationS =
                                0.0,

                            beginShapeIndex =
                                3,

                            endShapeIndex =
                                3,
                        )
                    ),
            )

        assertEquals(
            1,
            events.size
        )

        assertEquals(
            2,
            events.first()
                .anchor
                .shapeSegmentIndex
        )

        assertEquals(
            1.0,
            events.first()
                .anchor
                .segmentFraction,
            0.0,
        )
    }

    @Test
    fun diagnosticProgressEndsExactlyAtRouteEnd() {
        val anchors =
            buildDiagnosticProgressAnchors(
                routePointCount =
                    20,

                stepCount =
                    6,
            )

        assertEquals(
            6,
            anchors.size
        )

        assertEquals(
            18,
            anchors.last()
                .shapeSegmentIndex
        )

        assertEquals(
            1.0,
            anchors.last()
                .segmentFraction,
            0.0,
        )
    }
}