package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationViaCandidateRouterTest {
    @Test
    fun cancellationSuppressesStaleCallbacks() {
        val source = DeferredSource()
        var callbackCount = 0
        val handle =
            NavigationViaCandidateRouter(source).evaluate(
                request(),
                listOf("market" to RoutePoint(48.1, 11.1)),
            ) { callbackCount += 1 }

        handle.cancel()
        source.completeAll()
        assertEquals(0, callbackCount)
    }

    @Test
    fun routesBaselineAndEveryCandidateBeforeSelection() {
        val source = FixtureSource()
        var result: NavigationViaCandidateSelection? = null
        NavigationViaCandidateRouter(
            source,
            NavigationViaCandidateSelector(ambiguityDurationS = 10.0),
        ).evaluate(
            baseRequest = request(),
            candidates =
                listOf(
                    "market-a" to RoutePoint(48.1, 11.1),
                    "market-b" to RoutePoint(48.12, 11.12),
                ),
        ) { result = it.getOrThrow() }

        assertEquals(3, source.requests.size)
        assertTrue(result is NavigationViaCandidateSelection.Selected)
        assertEquals(
            "market-a",
            (result as NavigationViaCandidateSelection.Selected)
                .candidate.candidateId,
        )
    }

    @Test
    fun failedCandidateIsNeverInventedOrSelected() {
        val source = FixtureSource(failSecondCandidate = true)
        var result: NavigationViaCandidateSelection? = null
        NavigationViaCandidateRouter(source).evaluate(
            request(),
            listOf(
                "market-a" to RoutePoint(48.1, 11.1),
                "market-b" to RoutePoint(48.12, 11.12),
            ),
        ) { result = it.getOrThrow() }

        assertTrue(result is NavigationViaCandidateSelection.Selected)
        assertEquals(
            "market-a",
            (result as NavigationViaCandidateSelection.Selected)
                .candidate.candidateId,
        )
    }

    @Test
    fun baselineFailureFailsClosedWithoutCandidateRouting() {
        val source = FixtureSource(failBaseline = true)
        var failed = false
        NavigationViaCandidateRouter(source).evaluate(
            request(),
            listOf("market" to RoutePoint(48.1, 11.1)),
        ) { failed = it.isFailure }

        assertTrue(failed)
        assertEquals(1, source.requests.size)
    }

    private fun request() =
        NavigationRouteRequest(
            origin = RoutePoint(48.0, 11.0),
            destination = RoutePoint(48.2, 11.2),
        )

    private class DeferredSource : NavigationRouteSource {
        private val callbacks =
            mutableListOf<(Result<NavigationRouteContract>) -> Unit>()

        override fun acquire(
            request: NavigationRouteRequest,
            onResult: (Result<NavigationRouteContract>) -> Unit,
        ): NavigationRouteAcquisitionHandle {
            callbacks += onResult
            return object : NavigationRouteAcquisitionHandle {
                override fun cancel() = Unit
            }
        }

        fun completeAll() {
            callbacks.toList().forEachIndexed { index, callback ->
                callback(
                    Result.success(
                        NavigationRouteContract(
                            routeId = "deferred-$index",
                            family = NavigationRouteFamily.ProfileOptimal,
                            distanceM = 10_000.0,
                            durationS = 1_000.0,
                            geometry =
                                listOf(
                                    RoutePoint(48.0, 11.0),
                                    RoutePoint(48.2, 11.2),
                                ),
                            maneuvers = emptyList(),
                            engineName = "fixture",
                            engineVersion = "1",
                            segmentDataStatus =
                                NavigationRouteSegmentDataStatus.Complete,
                            diagnostics = emptyList(),
                        )
                    )
                )
            }
        }

        override fun close() = Unit
    }

    private class FixtureSource(
        private val failBaseline: Boolean = false,
        private val failSecondCandidate: Boolean = false,
    ) : NavigationRouteSource {
        val requests = mutableListOf<NavigationRouteRequest>()

        override fun acquire(
            request: NavigationRouteRequest,
            onResult: (Result<NavigationRouteContract>) -> Unit,
        ): NavigationRouteAcquisitionHandle {
            requests += request
            val index = requests.lastIndex
            val failure =
                (index == 0 && failBaseline) ||
                    (index == 2 && failSecondCandidate)
            if (failure) {
                onResult(Result.failure(IllegalStateException("fixture failure")))
            } else {
                val viaPenalty =
                    when (index) {
                        1 -> 100.0
                        2 -> 300.0
                        else -> 0.0
                    }
                onResult(
                    Result.success(
                        NavigationRouteContract(
                            routeId = "route-$index",
                            family = NavigationRouteFamily.ProfileOptimal,
                            distanceM = 10_000.0 + viaPenalty * 10.0,
                            durationS = 1_000.0 + viaPenalty,
                            geometry = listOf(request.origin, request.destination),
                            maneuvers = emptyList(),
                            engineName = "fixture",
                            engineVersion = "1",
                            segmentDataStatus =
                                NavigationRouteSegmentDataStatus.Complete,
                            diagnostics = emptyList(),
                        )
                    )
                )
            }
            return object : NavigationRouteAcquisitionHandle {
                override fun cancel() = Unit
            }
        }

        override fun close() = Unit
    }
}
