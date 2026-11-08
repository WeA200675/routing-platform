package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationPoiRankerTest {
    @Test
    fun prefersOpenNearAndFreshPoi() {
        val result = NavigationPoiRanker.rank(
            listOf(
                poi("far", 20_000.0, 900, true),
                poi("near", 1_000.0, 60, true),
            ),
            nowEpochMs = 10_000,
        )
        assertEquals("near", result.first().id)
    }

    @Test
    fun staleAndClosedPoiLosePriority() {
        val result = NavigationPoiRanker.rank(
            listOf(
                poi("stale-closed", 500.0, 20, false, capturedAt = 0),
                poi("fresh-open", 2_000.0, 90, true, capturedAt = 9_500),
            ),
            nowEpochMs = 10_000,
        )
        assertEquals("fresh-open", result.first().id)
    }

    private fun poi(
        id: String,
        distance: Double,
        detour: Int,
        open: Boolean,
        capturedAt: Long = 9_500,
    ) = NavigationPoi(
        id = id,
        name = id,
        category = PoiCategory.FUEL,
        latitude = 48.0,
        longitude = 11.0,
        distanceMeters = distance,
        detourSeconds = detour,
        priceCents = 170,
        openNow = open,
        capturedAtEpochMs = capturedAt,
        source = "test",
    )
}
