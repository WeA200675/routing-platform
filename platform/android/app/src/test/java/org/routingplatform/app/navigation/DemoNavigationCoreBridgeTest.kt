package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoNavigationCoreBridgeTest {

    @Test
    fun followsPreviewNavigatingArrivedStateMachine() {
        val bridge =
            DemoNavigationCoreBridge()

        val preview =
            bridge.currentSnapshot()

        assertEquals(
            NavigationSessionState.Preview,
            preview.state,
        )

        assertEquals(
            0.0,
            preview.progressFraction,
            0.0,
        )

        val started =
            bridge.startNavigation()

        assertEquals(
            NavigationSessionState.Navigating,
            started.state,
        )

        var snapshot =
            started

        repeat(4) {
            val previous =
                snapshot.progressFraction

            snapshot =
                bridge.advanceDemo()

            assertTrue(
                snapshot.progressFraction >=
                    previous
            )
        }

        assertEquals(
            NavigationSessionState.Arrived,
            snapshot.state,
        )

        assertTrue(
            snapshot.arrived
        )

        assertEquals(
            1.0,
            snapshot.progressFraction,
            0.0,
        )

        assertEquals(
            0.0,
            snapshot.remainingDistanceM,
            0.0,
        )

        assertFalse(
            snapshot.rerouteRequested
        )
    }
}
