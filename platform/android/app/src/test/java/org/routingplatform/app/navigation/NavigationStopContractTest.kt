package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStopContractTest {

    @Test
    fun stopPreservesRouteAndProgressAndCanRestart() {
        val bridge =
            DemoNavigationCoreBridge()

        bridge.startNavigation()

        val progressed =
            bridge.updateProgress(
                shapeSegmentIndex =
                    1,

                segmentFraction =
                    0.5,
            )

        val stopped =
            bridge.stopNavigation()

        assertEquals(
            NavigationSessionState.Preview,
            stopped.state,
        )

        assertEquals(
            progressed.sessionId,
            stopped.sessionId,
        )

        assertEquals(
            progressed.routeId,
            stopped.routeId,
        )

        assertEquals(
            progressed.shapeSegmentIndex,
            stopped.shapeSegmentIndex,
        )

        assertEquals(
            progressed.segmentFraction,
            stopped.segmentFraction,
            0.000001,
        )

        assertEquals(
            progressed.progressFraction,
            stopped.progressFraction,
            0.000001,
        )

        val restarted =
            bridge.startNavigation()

        assertEquals(
            NavigationSessionState.Navigating,
            restarted.state,
        )

        assertEquals(
            stopped.progressFraction,
            restarted.progressFraction,
            0.000001,
        )
    }
}