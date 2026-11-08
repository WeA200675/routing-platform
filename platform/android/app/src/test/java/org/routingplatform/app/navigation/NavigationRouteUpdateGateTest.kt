package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteUpdateGateTest {
    @Test
    fun acceptsOnlyExpectedSafeCompleteCandidate() {
        val gate = NavigationRouteUpdateGate()
        gate.expect(4L)
        val result = gate.apply(
            RouteUpdateCandidate(
                requestId = 4L,
                routeId = "replacement",
                score = RouteCandidateScore("replacement", 0.95, 100.0, 1.0, 0.9),
                geometryPointCount = 12,
            ),
        )
        assertEquals(RouteUpdateResult.ACCEPTED, result)
        assertEquals("replacement", gate.activeRouteId())
    }

    @Test
    fun rejectsUnexpectedOrUnsafeCandidate() {
        val gate = NavigationRouteUpdateGate()
        gate.expect(4L)
        assertEquals(
            RouteUpdateResult.REJECTED_NO_PENDING_REQUEST,
            gate.apply(
                RouteUpdateCandidate(
                    3L,
                    "wrong",
                    RouteCandidateScore("wrong", 0.99, 1.0, 1.0, 0.9),
                    10,
                ),
            ),
        )
        assertEquals(
            RouteUpdateResult.REJECTED_UNSAFE,
            gate.apply(
                RouteUpdateCandidate(
                    4L,
                    "unsafe",
                    RouteCandidateScore("unsafe", 0.5, 1.0, 1.0, 0.9),
                    10,
                ),
            ),
        )
    }
}
