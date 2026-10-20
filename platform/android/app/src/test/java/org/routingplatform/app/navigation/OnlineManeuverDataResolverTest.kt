package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineManeuverDataResolverTest {
    @Test fun usesDirectionSpecificLaneTagsForMatchedCarriageway() {
        val lanes = OnlineManeuverDataResolver.parseLanes(
            mapOf(
                "highway" to "motorway",
                "oneway" to "yes",
                "turn:lanes" to "through|through;right",
                "turn:lanes:backward" to "left|through",
            ),
            forward = false,
            type = ManeuverType.TurnLeft,
        )
        assertEquals(2, lanes?.size)
        assertTrue(lanes!![0].contains("←"))
        assertTrue(lanes[0].contains("✓"))
        assertTrue(lanes[1].contains("↑"))
        assertTrue(!lanes[1].contains("✓"))
    }

    @Test fun doesNotUseAmbiguousGenericTagsOnTwoWayRoads() {
        assertNull(
            OnlineManeuverDataResolver.parseLanes(
                mapOf("highway" to "primary", "turn:lanes" to "left|through"),
                forward = true,
                type = ManeuverType.TurnLeft,
            ),
        )
    }

    @Test fun supportsGenericTagsOnOneWayRoadsAndKeepsLaneCount() {
        val lanes = OnlineManeuverDataResolver.parseLanes(
            mapOf("highway" to "motorway_link", "oneway" to "yes", "turn:lanes" to "through|right"),
            forward = true,
            type = ManeuverType.Exit,
            side = NavigationManeuverSide.Right,
        )
        assertEquals(2, lanes?.size)
        assertTrue(lanes!![1].contains("→"))
        assertTrue(lanes[1].contains("✓"))
    }

    @Test fun doesNotMarkExitLaneWithoutKnownExitSideAsRecommended() {
        val lanes = OnlineManeuverDataResolver.parseLanes(
            mapOf("turn:lanes:forward" to "through|right"),
            forward = true,
            type = ManeuverType.Exit,
        )
        assertEquals(2, lanes?.size)
        assertTrue(lanes!!.none { it.contains("✓") })
    }
}
