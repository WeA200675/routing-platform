package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLiveIntelligenceTest {
    @Test
    fun safetyEventsOutrankComfortEvents() {
        val now = 1_000L
        val result = NavigationLiveEventPrioritizer.prioritize(
            listOf(
                event("poi", LiveEventKind.POI_UPDATE, 1),
                event("hazard", LiveEventKind.HAZARD, 5),
                event("traffic", LiveEventKind.TRAFFIC_JAM, 3),
            ),
            now,
        )

        assertEquals("hazard", result.first().event.id)
        assertTrue(result.first().priorityScore > result.last().priorityScore)
    }

    @Test
    fun expiredAndInvalidEventsAreDiscarded() {
        val result = NavigationLiveEventPrioritizer.prioritize(
            listOf(
                event("expired", LiveEventKind.HAZARD, 5, validUntil = 999),
                event("invalid", LiveEventKind.HAZARD, 5, latitude = 100.0),
                event("valid", LiveEventKind.HAZARD, 1),
            ),
            1_000,
        )

        assertEquals(listOf("valid"), result.map { it.event.id })
    }

    private fun event(
        id: String,
        kind: LiveEventKind,
        severity: Int,
        latitude: Double = 48.0,
        validUntil: Long = 10_000,
    ) = NavigationLiveEvent(
        id = id,
        kind = kind,
        latitude = latitude,
        longitude = 11.0,
        severity = severity,
        confidence = 0.9,
        capturedAtEpochMs = 0,
        validUntilEpochMs = validUntil,
        source = "test",
    )
}
