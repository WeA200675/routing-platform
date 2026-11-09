package org.routingplatform.app.navigation

enum class NavigationPoiCategory {
    FUEL,
    CHARGING,
    RESTAURANT,
    CAFE,
    PARKING,
    OTHER,
}

data class NavigationPoiRecommendation(
    val event: NavigationLiveEvent,
    val category: NavigationPoiCategory,
    val title: String,
    val distanceMeters: Double,
)

object NavigationPoiRecommendationEngine {
    fun recommend(
        events: List<NavigationLiveEvent>,
        currentPosition: RoutePoint,
        nowEpochMs: Long,
        maxResults: Int = 8,
    ): List<NavigationPoiRecommendation> =
        events.asSequence()
            .filter { it.kind == LiveEventKind.POI_UPDATE && it.isValid(nowEpochMs) }
            .map { event ->
                val category = categoryFromSource(event.source)
                NavigationPoiRecommendation(
                    event = event,
                    category = category,
                    title = titleFromSource(event.source, category),
                    distanceMeters = distanceMeters(
                        currentPosition.latitude,
                        currentPosition.longitude,
                        event.latitude,
                        event.longitude,
                    ),
                )
            }
            .sortedWith(compareBy<NavigationPoiRecommendation> { it.distanceMeters }.thenByDescending { it.event.confidence })
            .take(maxResults.coerceIn(1, 50))
            .toList()

    private fun categoryFromSource(source: String): NavigationPoiCategory {
        val value = source.lowercase()
        return when {
            "charging_station" in value || "charging" in value -> NavigationPoiCategory.CHARGING
            "fuel" in value -> NavigationPoiCategory.FUEL
            "restaurant" in value -> NavigationPoiCategory.RESTAURANT
            "cafe" in value -> NavigationPoiCategory.CAFE
            "parking" in value -> NavigationPoiCategory.PARKING
            else -> NavigationPoiCategory.OTHER
        }
    }

    private fun titleFromSource(source: String, category: NavigationPoiCategory): String {
        val name = source.substringAfterLast(':', missingDelimiterValue = "").trim()
        return name.takeIf { it.isNotEmpty() } ?: category.name.lowercase()
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return earth * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}
