package org.routingplatform.app.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLiveRouteCandidateScorerTest {
    @Test
    fun closureNearRouteMakesCandidateUnsafeAndSlower() {
        val route = NavigationRouteContract(
            routeId = "r1",
            family = NavigationRouteFamily.Fastest,
            distanceM = 10_000.0,
            durationS = 600.0,
            geometry = listOf(
                RoutePoint(48.0, 11.0),
                RoutePoint(48.01, 11.01),
            ),
            maneuvers = emptyList(),
            engineName = "test",
            engineVersion = "1",
            segmentDataStatus = NavigationRouteSegmentDataStatus.Complete,
            diagnostics = emptyList(),
        )
        val event = NavigationLiveEvent(
            id = "closure",
            kind = LiveEventKind.ROAD_CLOSURE,
            latitude = 48.005,
            longitude = 11.005,
            severity = 5,
            confidence = 1.0,
            capturedAtEpochMs = 1_000,
            validUntilEpochMs = 10_000,
            source = "test",
        )
        val score = NavigationLiveRouteCandidateScorer.score(route, listOf(event), 2_000)
        assertTrue(score.safetyScore < 0.5)
        assertTrue(score.timeSeconds > route.durationS)
    }
}
