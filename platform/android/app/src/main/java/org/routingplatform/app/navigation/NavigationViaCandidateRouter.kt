package org.routingplatform.app.navigation

/**
 * Acquires a baseline route and one real engine route per via candidate.
 * Failed candidate routes are excluded; no geometric approximation is used.
 */
class NavigationViaCandidateRouter(
    private val source: NavigationRouteSource,
    private val selector: NavigationViaCandidateSelector =
        NavigationViaCandidateSelector(),
) {
    fun evaluate(
        baseRequest: NavigationRouteRequest,
        candidates: List<Pair<String, RoutePoint>>,
        onResult: (Result<NavigationViaCandidateSelection>) -> Unit,
    ): NavigationRouteAcquisitionHandle {
        require(baseRequest.viaPoints.isEmpty()) {
            "Candidate evaluation requires a via-free baseline request."
        }

        val lock = Any()
        var cancelled = false
        var baseline: NavigationRouteContract? = null
        var remaining = candidates.size
        val routed = mutableListOf<NavigationViaCandidateRoute>()
        val handles = mutableListOf<NavigationRouteAcquisitionHandle>()

        fun finishIfReady() {
            val base = baseline ?: return
            if (remaining != 0 || cancelled) return
            cancelled = true
            onResult(Result.success(selector.select(base, routed.toList())))
        }

        fun acquireCandidates(base: NavigationRouteContract) {
            if (candidates.isEmpty()) {
                synchronized(lock) {
                    baseline = base
                    finishIfReady()
                }
                return
            }
            candidates.forEach { (id, point) ->
                val request =
                    baseRequest.copy(viaPoints = listOf(point))
                val handle =
                    source.acquire(request) { result ->
                        synchronized(lock) {
                            if (cancelled) return@acquire
                            result.getOrNull()?.let { route ->
                                routed +=
                                    NavigationViaCandidateRoute(
                                        candidateId = id,
                                        request = request,
                                        route = route,
                                    )
                            }
                            remaining -= 1
                            finishIfReady()
                        }
                    }
                synchronized(lock) {
                    if (!cancelled) handles += handle else handle.cancel()
                }
            }
        }

        val baselineHandle =
            source.acquire(baseRequest) { result ->
                result.fold(
                    onSuccess = { base ->
                        synchronized(lock) {
                            if (cancelled) return@fold
                            baseline = base
                        }
                        acquireCandidates(base)
                    },
                    onFailure = { error ->
                        synchronized(lock) {
                            if (cancelled) return@fold
                            cancelled = true
                        }
                        onResult(Result.failure(error))
                    },
                )
            }
        synchronized(lock) {
            if (!cancelled) handles += baselineHandle else baselineHandle.cancel()
        }

        return object : NavigationRouteAcquisitionHandle {
            override fun cancel() {
                val toCancel =
                    synchronized(lock) {
                        if (cancelled) return
                        cancelled = true
                        handles.toList()
                    }
                toCancel.forEach { it.cancel() }
            }
        }
    }
}
