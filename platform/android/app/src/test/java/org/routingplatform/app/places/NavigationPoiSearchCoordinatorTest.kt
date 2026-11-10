package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPoiSearchCoordinatorTest {
    @Test
    fun usesFallbackAndAppliesRanking() {
        val offline = object : PoiSearchSource {
            override fun search(request: PoiSearchRequest) = listOf(
                NavigationPoi(
                    id = "offline",
                    name = "Offline Tankstelle",
                    category = PoiCategory.FUEL,
                    latitude = 48.0,
                    longitude = 11.0,
                    distanceMeters = 1_000.0,
                    detourSeconds = 60,
                    priceCents = 170,
                    openNow = true,
                    capturedAtEpochMs = 9_900,
                    source = "offline",
                ),
            )
        }
        val coordinator = NavigationPoiSearchCoordinator(
            FallbackPoiSearchSource(
                listOf(
                    object : PoiSearchSource {
                        override fun search(request: PoiSearchRequest): List<NavigationPoi> = emptyList()
                    },
                    offline,
                ),
            ),
        )

        val result = coordinator.search(
            PoiSearchRequest(category = PoiCategory.FUEL, maxResults = 1),
            nowEpochMs = 10_000,
        )

        assertEquals(listOf("offline"), result.map { it.id })
    }
}
