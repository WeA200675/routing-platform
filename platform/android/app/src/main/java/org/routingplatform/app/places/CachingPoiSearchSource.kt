package org.routingplatform.app.places

/** Cache-first POI source with deterministic de-duplication and provider fallback. */
class CachingPoiSearchSource(
    private val cache: NavigationPoiCache,
    private val upstream: PoiSearchSource,
) : PoiSearchSource {
    override fun search(request: PoiSearchRequest): List<NavigationPoi> {
        val now = System.currentTimeMillis()
        val cached = cache.search(request.copy(query = null), now)
        val fresh = runCatching { upstream.search(request) }.getOrDefault(emptyList())
        val merged = (cached + fresh)
            .distinctBy { poi -> "${poi.category}:${poi.latitude}:${poi.longitude}:${poi.name.lowercase()}" }
            .take(request.maxResults.coerceAtLeast(1))
        merged.forEach(cache::put)
        return merged
    }
}
