package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPoiCacheTest {
    @Test
    fun evictsOldestEntryAtCapacity() {
        val cache = NavigationPoiCache(maxEntries = 2, maxAgeMs = 10_000)
        cache.put(poi("a", 1))
        cache.put(poi("b", 2))
        cache.put(poi("c", 3))

        assertEquals(2, cache.size())
        assertEquals(listOf("b", "c"), cache.search(PoiSearchRequest(maxResults = 10), 4).map { it.id })
    }

    @Test
    fun removesExpiredEntriesAndFiltersCategory() {
        val cache = NavigationPoiCache(maxEntries = 10, maxAgeMs = 100)
        cache.put(poi("fuel", 1, PoiCategory.FUEL))
        cache.put(poi("parking", 1, PoiCategory.PARKING))

        assertEquals(
            listOf("fuel"),
            cache.search(PoiSearchRequest(category = PoiCategory.FUEL), 50).map { it.id },
        )
        assertEquals(emptyList<NavigationPoi>(), cache.search(PoiSearchRequest(), 200))
    }

    private fun poi(id: String, capturedAt: Long, category: PoiCategory = PoiCategory.FUEL) =
        NavigationPoi(
            id = id,
            name = id,
            category = category,
            latitude = 48.0,
            longitude = 11.0,
            distanceMeters = 1_000.0,
            capturedAtEpochMs = capturedAt,
            openNow = true,
            source = "test",
        )
}
