package org.routingplatform.app.places

class NavigationPoiCache(
    private val maxEntries: Int = 1_000,
    private val maxAgeMs: Long = 24 * 60 * 60 * 1_000L,
) {
    private val entries = LinkedHashMap<String, NavigationPoi>(maxEntries, 0.75f, true)

    init {
        require(maxEntries > 0)
        require(maxAgeMs > 0)
    }

    @Synchronized
    fun put(poi: NavigationPoi) {
        entries[poi.id] = poi
        while (entries.size > maxEntries) {
            entries.entries.iterator().apply {
                if (hasNext()) {
                    next()
                    remove()
                }
            }
        }
    }

    @Synchronized
    fun search(
        request: PoiSearchRequest,
        nowEpochMs: Long,
    ): List<NavigationPoi> {
        entries.entries.removeIf { nowEpochMs - it.value.capturedAtEpochMs > maxAgeMs }
        return entries.values
            .asSequence()
            .filter { poi ->
                (request.category == null || poi.category == request.category) &&
                    (request.query.isNullOrBlank() ||
                        poi.name.contains(request.query, ignoreCase = true))
            }
            .take(request.maxResults.coerceAtLeast(1))
            .toList()
    }

    @Synchronized
    fun size(): Int = entries.size
}
