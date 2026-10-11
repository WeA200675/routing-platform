package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSafetyContractTest {

    @Test
    fun demoBridgeNeverCrossesCoreBoundary() {
        val bridge =
            DemoNavigationCoreBridge()

        val snapshots =
            mutableListOf(
                bridge.currentSnapshot(),
                bridge.startNavigation(),
            )

        repeat(4) {
            snapshots +=
                bridge.advanceDemo()
        }

        snapshots.forEach {
                snapshot ->

            assertTrue(
                snapshot
                    .presentationBoundaryIntact
            )

            assertFalse(
                snapshot
                    .rerouteRequested
            )

            assertFalse(
                snapshot
                    .routeRecomputed
            )

            assertFalse(
                snapshot
                    .routingEngineInvoked
            )

            assertFalse(
                snapshot
                    .candidateSelectionInvoked
            )

            assertFalse(
                snapshot
                    .costEngineInvoked
            )

            assertFalse(
                snapshot
                    .productionRouteMutationAllowed
            )
        }
    }
}
