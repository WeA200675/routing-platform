package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.ProfileMapOrientation

class NavigationCompassPresentationTest {

    @Test
    fun trustedHeadingMayRotateHeadingUpMapWhenCameraGateIsOpen() {
        val presentation =
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        ProfileMapOrientation.HeadingUp,

                    trustedTravelBearingDegrees =
                        90.0,

                    cameraFollowAllowed =
                        true,
                )

        assertTrue(
            presentation.headingUpActive
        )

        assertEquals(
            90.0,
            presentation.mapBearingDegrees,
            0.001,
        )

        assertEquals(
            -90.0,
            presentation.compassNorthRotationDegrees,
            0.001,
        )

        assertEquals(
            "90°",
            presentation.headingLabel,
        )
    }

    @Test
    fun closedCameraGateForcesNorthUpEvenWithTrustedHeading() {
        val presentation =
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        ProfileMapOrientation.HeadingUp,

                    trustedTravelBearingDegrees =
                        123.0,

                    cameraFollowAllowed =
                        false,
                )

        assertFalse(
            presentation.headingUpActive
        )

        assertEquals(
            0.0,
            presentation.mapBearingDegrees,
            0.001,
        )

        assertEquals(
            0.0,
            presentation.compassNorthRotationDegrees,
            0.001,
        )

        assertEquals(
            "123°",
            presentation.headingLabel,
        )
    }

    @Test
    fun explicitNorthUpNeverRotatesMap() {
        val presentation =
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        ProfileMapOrientation.NorthUp,

                    trustedTravelBearingDegrees =
                        215.0,

                    cameraFollowAllowed =
                        true,
                )

        assertFalse(
            presentation.headingUpActive
        )

        assertEquals(
            0.0,
            presentation.mapBearingDegrees,
            0.001,
        )

        assertEquals(
            "215°",
            presentation.headingLabel,
        )
    }

    @Test
    fun missingHeadingFallsBackToNorthUpWithoutDuplicateCardinalLabel() {
        val presentation =
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        ProfileMapOrientation.HeadingUp,

                    trustedTravelBearingDegrees =
                        null,

                    cameraFollowAllowed =
                        true,
                )

        assertFalse(
            presentation.headingUpActive
        )

        assertEquals(
            0.0,
            presentation.mapBearingDegrees,
            0.001,
        )

        assertNull(
            presentation.headingLabel
        )
    }

    @Test
    fun compassNorthUsesShortestInverseRotation() {
        val presentation =
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        ProfileMapOrientation.HeadingUp,

                    trustedTravelBearingDegrees =
                        350.0,

                    cameraFollowAllowed =
                        true,
                )

        assertEquals(
            350.0,
            presentation.mapBearingDegrees,
            0.001,
        )

        assertEquals(
            10.0,
            presentation.compassNorthRotationDegrees,
            0.001,
        )
    }
}