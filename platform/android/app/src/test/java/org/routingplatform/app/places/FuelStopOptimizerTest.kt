package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class FuelStopOptimizerTest {
    @Test
    fun ignoresExpiredQuotesAndChoosesLowestDetour() {
        val result = FuelStopOptimizer.choose(
            listOf(
                candidate("expired", detour = 30, cents = 150, validUntil = 900),
                candidate("valid", detour = 60, cents = 170, validUntil = 2_000),
            ),
            nowEpochMs = 1_000,
        )
        assertEquals("valid", result?.station?.id)
    }

    private fun candidate(
        id: String,
        detour: Int,
        cents: Int,
        validUntil: Long,
    ) = FuelStopCandidate(
        station = NavigationPoi(
            id = id,
            name = id,
            category = PoiCategory.FUEL,
            latitude = 48.0,
            longitude = 11.0,
            distanceMeters = 5_000.0,
            detourSeconds = detour,
            openNow = true,
            capturedAtEpochMs = 0,
            source = "test",
        ),
        quote = FuelPriceQuote(id, cents, 0, validUntil, "test"),
        expectedFuelLiters = 30.0,
        reserveAfterStopMeters = 10_000.0,
    )
}
