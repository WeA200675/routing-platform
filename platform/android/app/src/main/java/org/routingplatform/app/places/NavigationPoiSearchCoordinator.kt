package org.routingplatform.app.places

class NavigationPoiSearchCoordinator(
    private val source: PoiSearchSource,
) {
    fun search(
        request: PoiSearchRequest,
        nowEpochMs: Long,
        weights: PoiRankingWeights = PoiRankingWeights(),
    ): List<NavigationPoi> =
        NavigationPoiRanker.rank(
            pois = source.search(request).take(request.maxResults.coerceAtLeast(1)),
            nowEpochMs = nowEpochMs,
            weights = weights,
        ).take(request.maxResults.coerceAtLeast(1))
}

