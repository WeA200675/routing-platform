package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeNavigationSnapshotTest {

    @Test
    fun preservesNativeShapeProgress() {
        val nativeSnapshot =
            NativeNavigationSnapshot(
                schemaVersion =
                    1,

                sessionId =
                    "native-test-session",

                stateOrdinal =
                    1,

                routeId =
                    "native-test-route",

                routeDistanceM =
                    3000.0,

                routeDurationS =
                    600.0,

                geometryLatLon =
                    doubleArrayOf(
                        47.1400,
                        9.5200,
                        47.1450,
                        9.5200,
                        47.1500,
                        9.5200,
                        47.1550,
                        9.5200,
                    ),

                shapeSegmentIndex =
                    1,

                segmentFraction =
                    0.5,

                progressFraction =
                    0.5,

                remainingDistanceM =
                    1500.0,

                remainingDurationS =
                    300.0,

                currentInstruction =
                    "Continue straight",

                distanceToCurrentManeuverEndM =
                    500.0,

                arrived =
                    false,

                rerouteRequested =
                    false,

                routeRecomputed =
                    false,

                routingEngineInvoked =
                    false,

                candidateSelectionInvoked =
                    false,

                costEngineInvoked =
                    false,

                productionRouteMutationAllowed =
                    false,
            )

        val snapshot =
            nativeSnapshot.toUiSnapshot()

        assertEquals(
            1,
            snapshot.shapeSegmentIndex,
        )

        assertEquals(
            0.5,
            snapshot.segmentFraction,
            0.0,
        )

        assertEquals(
            0.5,
            snapshot.progressFraction,
            0.0,
        )

        assertEquals(
            NavigationSessionState.Navigating,
            snapshot.state,
        )

        assertTrue(
            snapshot.presentationBoundaryIntact
        )
    }
}