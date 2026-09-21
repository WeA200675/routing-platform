package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationViaCandidateSelectorTest {
    private val origin = RoutePoint(48.0, 11.0)
    private val destination = RoutePoint(48.2, 11.2)

    @Test
    fun selectsOnlyClearlyBestEngineRoutedDetour() {
        val selector =
            NavigationViaCandidateSelector(
                maximumAutomaticAddedDurationS = 600.0,
                ambiguityDurationS = 60.0,
            )
        val result =
            selector.select(
                baseline = route("base", 1000.0, 10000.0),
                candidates =
                    listOf(
                        candidate("b", 1300.0, 12000.0),
                        candidate("a", 1100.0, 11500.0),
                    ),
            )
        assertTrue(result is NavigationViaCandidateSelection.Selected)
        result as NavigationViaCandidateSelection.Selected
        assertEquals("a", result.candidate.candidateId)
        assertEquals(100.0, result.addedDurationS, 0.0)
        assertEquals(1500.0, result.addedDistanceM, 0.0)
    }

    @Test
    fun closeEngineRoutesRequireUserClarification() {
        val result =
            NavigationViaCandidateSelector(
                ambiguityDurationS = 60.0
            ).select(
                baseline = route("base", 1000.0, 10000.0),
                candidates =
                    listOf(
                        candidate("a", 1100.0, 11000.0),
                        candidate("b", 1140.0, 10500.0),
                    ),
            )
        assertTrue(result is NavigationViaCandidateSelection.ClarificationRequired)
    }

    @Test
    fun excessiveDetourRequiresUserClarification() {
        val result =
            NavigationViaCandidateSelector(
                maximumAutomaticAddedDurationS = 300.0
            ).select(
                baseline = route("base", 1000.0, 10000.0),
                candidates = listOf(candidate("a", 1400.0, 12000.0)),
            )
        assertTrue(result is NavigationViaCandidateSelection.ClarificationRequired)
    }

    @Test
    fun noSuccessfulEngineRouteFailsClosed() {
        assertEquals(
            NavigationViaCandidateSelection.NoRoutableCandidate,
            NavigationViaCandidateSelector().select(
                baseline = route("base", 1000.0, 10000.0),
                candidates = emptyList(),
            ),
        )
    }

    @Test
    fun deterministicTieBreakDoesNotDependOnInputOrder() {
        val selector = NavigationViaCandidateSelector(ambiguityDurationS = 0.0)
        val first =
            selector.select(
                route("base", 1000.0, 10000.0),
                listOf(
                    candidate("b", 1100.0, 12000.0),
                    candidate("a", 1100.0, 11000.0),
                ),
            )
        val second =
            selector.select(
                route("base", 1000.0, 10000.0),
                listOf(
                    candidate("a", 1100.0, 11000.0),
                    candidate("b", 1100.0, 12000.0),
                ),
            )
        assertEquals(first, second)
    }

    private fun candidate(id: String, duration: Double, distance: Double) =
        NavigationViaCandidateRoute(
            candidateId = id,
            request =
                NavigationRouteRequest(
                    origin = origin,
                    destination = destination,
                    viaPoints = listOf(RoutePoint(48.1, 11.1)),
                ),
            route = route(id, duration, distance),
        )

    private fun route(id: String, duration: Double, distance: Double) =
        NavigationRouteContract(
            routeId = id,
            family = NavigationRouteFamily.ProfileOptimal,
            distanceM = distance,
            durationS = duration,
            geometry = listOf(origin, destination),
            maneuvers = emptyList(),
            engineName = "fixture",
            engineVersion = "1",
            segmentDataStatus = NavigationRouteSegmentDataStatus.Complete,
            diagnostics = emptyList(),
        )
}
