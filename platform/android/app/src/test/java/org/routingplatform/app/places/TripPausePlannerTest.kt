package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Test

class TripPausePlannerTest {
    @Test
    fun recommendsNearestRestStopWhenPauseIsDue() {
        val result = TripPausePlanner.recommend(
            PausePlanningInput(
                routeDurationSeconds = 10_000,
                elapsedDrivingSeconds = 8_000,
                lastPauseElapsedSeconds = 0,
                pauseIntervalSeconds = 7_200,
                nearbyRestStops = listOf(rest("rest", 120)),
            ),
        )
        assertEquals(true, result.required)
        assertEquals("rest", result.poiId)
    }

    @Test
    fun reportsMissingStopWithoutBlockingNavigation() {
        val result = TripPausePlanner.recommend(
            PausePlanningInput(
                routeDurationSeconds = 8_000,
                elapsedDrivingSeconds = 8_000,
                lastPauseElapsedSeconds = 0,
                pauseIntervalSeconds = 7_200,
            ),
        )
        assertEquals(true, result.required)
        assertEquals(null, result.poiId)
    }

    private fun rest(id: String, detour: Int) = NavigationPoi(
        id = id,
        name = id,
        category = PoiCategory.REST_AREA,
        latitude = 48.0,
        longitude = 11.0,
        distanceMeters = 1_000.0,
        detourSeconds = detour,
        openNow = true,
        capturedAtEpochMs = 0,
        source = "test",
    )
}
