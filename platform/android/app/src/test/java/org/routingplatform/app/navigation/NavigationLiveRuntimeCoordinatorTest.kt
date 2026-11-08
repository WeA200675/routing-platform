package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveRuntimeCoordinatorTest {
    @Test
    fun refreshesStoresAndPrioritizesIncomingEvents() {
        val provider = object : NavigationLiveEventProvider {
            override val providerId: String = "test"
            override fun fetch(nowEpochMs: Long) = listOf(
                NavigationLiveEvent(
                    id = "hazard",
                    kind = LiveEventKind.HAZARD,
                    latitude = 48.0,
                    longitude = 11.0,
                    severity = 5,
                    confidence = 1.0,
                    capturedAtEpochMs = nowEpochMs,
                    validUntilEpochMs = nowEpochMs + 10_000,
                    source = providerId,
                ),
            )
        }

        val snapshot = NavigationLiveRuntimeCoordinator(
            ResilientLiveEventProvider(listOf(provider)),
        ).refresh(1_000)

        assertEquals(1, snapshot.eventCount)
        assertEquals("hazard", snapshot.prioritizedEvents.first().event.id)
        assertEquals(1, snapshot.providerCount)
    }
}
