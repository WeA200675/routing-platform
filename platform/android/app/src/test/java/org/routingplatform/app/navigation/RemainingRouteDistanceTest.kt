package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingRouteDistanceTest {
    @Test
    fun calculatesRemainingDistanceFromProgress() {
        val geometry = listOf(
            RoutePoint(48.0, 11.0),
            RoutePoint(48.001, 11.0),
            RoutePoint(48.002, 11.0),
        )
        val result = remainingRouteDistanceKm(geometry, 0, 0.5)
        assertEquals(0.166, result, 0.02)
    }
}
