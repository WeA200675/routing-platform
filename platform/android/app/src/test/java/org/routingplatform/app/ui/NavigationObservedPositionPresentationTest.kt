package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationFusionMode
import org.routingplatform.app.navigation.NavigationPositionConfidence
import org.routingplatform.app.navigation.NavigationRouteProgressSafetyStatus
import org.routingplatform.app.navigation.RoutePoint

class NavigationObservedPositionPresentationTest {

    private val point =
        RoutePoint(
            latitude =
                47.141,

            longitude =
                9.521,
        )

    @Test
    fun absentObservedPositionProducesNoMapPresentation() {
        assertNull(
            NavigationObservedPositionPresentation
                .create(
                    position =
                        null,

                    accuracyM =
                        8.0,

                    confidence =
                        NavigationPositionConfidence.High,

                    safetyStatus =
                        NavigationRouteProgressSafetyStatus.Accepted,

                    fusionMode =
                        NavigationFusionMode.DirectObservation,
                )
        )
    }

    @Test
    fun acceptedHighDirectObservationMayDriveCamera() {
        val presentation =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            8.4,

                        confidence =
                            NavigationPositionConfidence.High,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.Accepted,

                        fusionMode =
                            NavigationFusionMode.DirectObservation,
                    )
            )

        assertTrue(
            presentation.cameraFollowAllowed
        )

        assertEquals(
            8.4,
            presentation.accuracyM!!,
            0.001,
        )

        assertEquals(
            8.4,
            presentation.accuracyRadiusM!!,
            0.001,
        )

        assertTrue(
            presentation.statusText
                .contains(
                    "Safety: akzeptiert"
                )
        )
    }

    @Test
    fun heldOffRouteObservationCannotMoveCamera() {
        val held =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            12.0,

                        confidence =
                            NavigationPositionConfidence.High,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.HeldOffRoute,

                        fusionMode =
                            NavigationFusionMode.DirectObservation,
                    )
            )

        val accepted =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            12.0,

                        confidence =
                            NavigationPositionConfidence.High,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.Accepted,

                        fusionMode =
                            NavigationFusionMode.DirectObservation,
                    )
            )

        assertFalse(
            held.cameraFollowAllowed
        )

        assertTrue(
            held.statusText
                .contains(
                    "Safety: Hold"
                )
        )

        assertNotEquals(
            accepted.markerColor,
            held.markerColor,
        )
    }

    @Test
    fun deadReckoningCannotDrivePresentationCamera() {
        val presentation =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            9.0,

                        confidence =
                            NavigationPositionConfidence.Medium,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.Accepted,

                        fusionMode =
                            NavigationFusionMode.DeadReckoning,
                    )
            )

        assertFalse(
            presentation.cameraFollowAllowed
        )
    }

    @Test
    fun lowConfidenceCannotDrivePresentationCamera() {
        val presentation =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            30.0,

                        confidence =
                            NavigationPositionConfidence.Low,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.Accepted,

                        fusionMode =
                            NavigationFusionMode.DirectObservation,
                    )
            )

        assertFalse(
            presentation.cameraFollowAllowed
        )
    }

    @Test
    fun accuracyBoundaryIsClosedAndBounded() {
        val boundary =
            NavigationObservedPositionPresentation
                .accuracyBoundary(
                    center =
                        point,

                    radiusM =
                        25.0,

                    segments =
                        48,
                )

        assertEquals(
            49,
            boundary.size,
        )

        assertEquals(
            boundary.first(),
            boundary.last(),
        )

        assertTrue(
            boundary.all {
                it.latitude in
                    -90.0..90.0 &&
                    it.longitude in
                        -180.0..180.0
            }
        )
    }

    @Test
    fun hugeAccuracyRemainsTextualButDoesNotCreateHugePolygon() {
        val presentation =
            requireNotNull(
                NavigationObservedPositionPresentation
                    .create(
                        position =
                            point,

                        accuracyM =
                            5_000.0,

                        confidence =
                            NavigationPositionConfidence.Low,

                        safetyStatus =
                            NavigationRouteProgressSafetyStatus.HeldLowConfidence,

                        fusionMode =
                            NavigationFusionMode.Rejected,
                    )
            )

        assertEquals(
            5_000.0,
            presentation.accuracyM!!,
            0.001,
        )

        assertNull(
            presentation.accuracyRadiusM
        )
    }
}