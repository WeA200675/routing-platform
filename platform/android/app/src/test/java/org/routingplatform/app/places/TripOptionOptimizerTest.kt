package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class TripOptionOptimizerTest {
    @Test
    fun rejectsUnsafeOptions() {
        val result = TripOptionOptimizer.choose(
            listOf(
                TripOption("unsafe", 100, 1_000.0, safetyScore = 0.4),
            ),
        )
        assertEquals(null, result)
    }

    @Test
    fun prioritizesSafetyBeforeSmallTimeAdvantage() {
        val result = TripOptionOptimizer.choose(
            listOf(
                TripOption("fast", 100, 1_000.0, safetyScore = 0.75),
                TripOption("safe", 120, 1_200.0, safetyScore = 0.98),
            ),
        )
        assertEquals("safe", result?.id)
    }

    @Test
    fun canPreferLowerCostWhenSafetyMatches() {
        val result = TripOptionOptimizer.choose(
            listOf(
                TripOption("expensive", 100, 1_000.0, fuelCents = 500, safetyScore = 0.9),
                TripOption("cheap", 105, 1_100.0, fuelCents = 100, safetyScore = 0.9),
            ),
        )
        assertEquals("cheap", result?.id)
    }
}
