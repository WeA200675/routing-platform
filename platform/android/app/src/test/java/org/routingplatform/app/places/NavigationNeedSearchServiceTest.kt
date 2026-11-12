package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationNeedSearchServiceTest {
    @Test
    fun filtersClosedAndLongDetourResultsForOpenNeed() {
        val service = NavigationNeedSearchService(
            NavigationPoiSearchCoordinator(
                object : PoiSearchSource {
                    override fun search(request: PoiSearchRequest): List<NavigationPoi> = listOf(
                        NavigationPoi("closed", "Apotheke alt", PoiCategory.PHARMACY, 48.0, 11.0, 100.0, 100, openNow = false, capturedAtEpochMs = 1, source = "test"),
                        NavigationPoi("open", "Apotheke", PoiCategory.PHARMACY, 48.0, 11.0, 200.0, 120, openNow = true, capturedAtEpochMs = 1, source = "test"),
                    )
                }
            )
        )
        val result = service.search("offene Apotheke ohne Umweg", 2)
        assertTrue(result is NavigationNeedSearchResult.Found)
        assertEquals(listOf("open"), (result as NavigationNeedSearchResult.Found).pois.map { it.id })
    }
}
