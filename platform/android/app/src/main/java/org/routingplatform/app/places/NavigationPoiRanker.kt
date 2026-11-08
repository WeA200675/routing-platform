package org.routingplatform.app.places

data class PoiRankingWeights(
    val distance: Double = 1.0,
    val detour: Double = 1.0,
    val price: Double = 0.8,
    val freshness: Double = 0.7,
    val openStatus: Double = 1.0,
)

object NavigationPoiRanker {
    fun rank(
        pois: List<NavigationPoi>,
        nowEpochMs: Long,
        weights: PoiRankingWeights = PoiRankingWeights(),
    ): List<NavigationPoi> =
        pois.asSequence()
            .filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
            .sortedByDescending { score(it, nowEpochMs, weights) }
            .toList()

    private fun score(
        poi: NavigationPoi,
        nowEpochMs: Long,
        weights: PoiRankingWeights,
    ): Double {
        val distanceScore = 1.0 / (1.0 + poi.distanceMeters.coerceAtLeast(0.0) / 1_000.0)
        val detourScore = 1.0 / (1.0 + (poi.detourSeconds ?: 900).coerceAtLeast(0) / 300.0)
        val freshnessAge = (nowEpochMs - poi.capturedAtEpochMs).coerceAtLeast(0L)
        val freshnessScore = 1.0 / (1.0 + freshnessAge / 3_600_000.0)
        val openScore = when (poi.openNow) {
            true -> 1.0
            false -> 0.0
            null -> 0.5
        }
        val priceScore = poi.priceCents?.let { 1.0 / (1.0 + it.coerceAtLeast(1) / 100.0) } ?: 0.5
        return distanceScore * weights.distance +
            detourScore * weights.detour +
            freshnessScore * weights.freshness +
            openScore * weights.openStatus +
            priceScore * weights.price
    }
}
