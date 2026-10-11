package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraZoomPresentationTest {

    @Test
    fun disabledAutomaticZoomKeepsProfileZoomExactly() {
        val result =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        false,

                    defaultZoom =
                        16.0,

                    distanceToManeuverM =
                        40.0,
                )

        assertEquals(
            NavigationCameraZoomStage.Fixed,
            result.stage,
        )

        assertEquals(
            16.0,
            result.zoom,
            0.0001,
        )

        assertFalse(
            result.automaticApplied
        )
    }

    @Test
    fun invalidManeuverDistanceFailsClosedToFixedZoom() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -1.0,
        ).forEach {
                distance ->

            val result =
                NavigationCameraZoomPolicy
                    .create(
                        automaticEnabled =
                            true,

                        defaultZoom =
                            16.0,

                        distanceToManeuverM =
                            distance,
                    )

            assertEquals(
                NavigationCameraZoomStage.Fixed,
                result.stage,
            )

            assertEquals(
                16.0,
                result.zoom,
                0.0001,
            )

            assertFalse(
                result.automaticApplied
            )
        }
    }

    @Test
    fun automaticStagesWidenCruiseAndTightenNearManeuver() {
        val cruise =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        16.0,

                    distanceToManeuverM =
                        1200.0,
                )

        val approach =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        16.0,

                    distanceToManeuverM =
                        800.0,
                )

        val near =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        16.0,

                    distanceToManeuverM =
                        250.0,
                )

        val immediate =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        16.0,

                    distanceToManeuverM =
                        80.0,
                )

        assertEquals(
            NavigationCameraZoomStage.Cruise,
            cruise.stage,
        )

        assertEquals(
            NavigationCameraZoomStage.Approach,
            approach.stage,
        )

        assertEquals(
            NavigationCameraZoomStage.Near,
            near.stage,
        )

        assertEquals(
            NavigationCameraZoomStage.Immediate,
            immediate.stage,
        )

        assertEquals(
            15.25,
            cruise.zoom,
            0.0001,
        )

        assertEquals(
            16.0,
            approach.zoom,
            0.0001,
        )

        assertEquals(
            16.75,
            near.zoom,
            0.0001,
        )

        assertEquals(
            17.5,
            immediate.zoom,
            0.0001,
        )

        assertTrue(
            cruise.zoom <
                approach.zoom
        )

        assertTrue(
            approach.zoom <
                near.zoom
        )

        assertTrue(
            near.zoom <
                immediate.zoom
        )

        assertTrue(
            immediate.automaticApplied
        )
    }

    @Test
    fun automaticOffsetsRespectSupportedMapZoomBounds() {
        val minimum =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        4.0,

                    distanceToManeuverM =
                        5000.0,
                )

        val maximum =
            NavigationCameraZoomPolicy
                .create(
                    automaticEnabled =
                        true,

                    defaultZoom =
                        22.0,

                    distanceToManeuverM =
                        10.0,
                )

        assertEquals(
            4.0,
            minimum.zoom,
            0.0001,
        )

        assertEquals(
            22.0,
            maximum.zoom,
            0.0001,
        )
    }
}
