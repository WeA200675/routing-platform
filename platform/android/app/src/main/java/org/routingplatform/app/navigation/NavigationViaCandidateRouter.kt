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
    ): NavigationRouteAcquisitionHandle =
        evaluateAll(baseRequest, candidates) { result ->
            onResult(
                result.map { evaluation ->
                    selector.select(
                        evaluation.baseline,
                        evaluation.rankedCandidates.map { it.candidate },
                    )
                }
            )
        }

    /**
     * Returns all routable candidates with actual added time and distance.
     * Existing via stops are preserved; the new candidate is inserted first.
     */
    fun evaluateAll(
        baseRequest: NavigationRouteRequest,
        candidates: List<Pair<String, RoutePoint>>,
        onResult: (Result<NavigationViaCandidateEvaluation>) -> Unit,
    ): NavigationRouteAcquisitionHandle {
        require(candidates.map { it.first }.toSet().size == candidates.size) {
            "Candidate ids must be unique."
        }
        require(candidates.all { it.first.isNotBlank() }) {
            "Candidate ids must not be blank."
        }

        val lock = Any()
        var cancelled = false
        var callbackDelivered = false
        var baseline: NavigationRouteContract? = null
        var remaining = candidates.size
        val routed = mutableListOf<NavigationViaCandidateRoute>()
        val handles = mutableListOf<NavigationRouteAcquisitionHandle>()

        fun finishIfReady() {
            val base = baseline ?: return
            if (remaining != 0 || cancelled) return
            cancelled = true
            callbackDelivered = true
            onResult(
                Result.success(
                    NavigationViaCandidateEvaluation(
                        baseline = base,
                        rankedCandidates = selector.rank(base, routed.toList()),
                    )
                )
            )
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
                val request = baseRequest.copy(
                    viaPoints = listOf(point) + baseRequest.viaPoints,
                )
                val handle = source.acquire(request) { result ->
                    synchronized(lock) {
                        if (cancelled) return@acquire
                        result.getOrNull()?.let { route ->
                            routed += NavigationViaCandidateRoute(
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

        val baselineHandle = source.acquire(baseRequest) { result ->
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
                        callbackDelivered = true
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
                val toCancel = synchronized(lock) {
                    if (cancelled || callbackDelivered) return
                    cancelled = true
                    handles.toList()
                }
                toCancel.forEach { it.cancel() }
            }
        }
    }
}
