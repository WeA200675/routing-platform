package org.routingplatform.app.places

enum class PoiCategory {
    FUEL,
    CHARGING,
    PARKING,
    REST_AREA,
    RESTAURANT,
    WORKSHOP,
    HOTEL,
    PHARMACY,
    SUPERMARKET,
    TOILET,
}

data class NavigationPoi(
    val id: String,
    val name: String,
    val category: PoiCategory,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val detourSeconds: Int? = null,
    val priceCents: Int? = null,
    val openNow: Boolean? = null,
    val capturedAtEpochMs: Long,
    val source: String,
)

data class PoiSearchRequest(
    val query: String? = null,
    val category: PoiCategory? = null,
    val alongRoute: Boolean = false,
    val maxResults: Int = 20,
)

interface PoiSearchSource {
    fun search(request: PoiSearchRequest): List<NavigationPoi>
}

class FallbackPoiSearchSource(
    private val sources: List<PoiSearchSource>,
) : PoiSearchSource {
    override fun search(request: PoiSearchRequest): List<NavigationPoi> {
        for (source in sources) {
            val result = runCatching { source.search(request) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }
}
