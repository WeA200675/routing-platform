package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveProviderRegistryTest {
    @Test
    fun cachedEventsRemainAvailableWhenProviderFails() {
        val event = NavigationLiveEvent(
            id = "hazard-1",
            kind = LiveEventKind.HAZARD,
            latitude = 48.0,
            longitude = 11.0,
            severity = 4,
            confidence = 1.0,
            capturedAtEpochMs = 1_000,
            validUntilEpochMs = 10_000,
            source = "test",
        )
        var fail = false
        val provider = object : NavigationLiveEventProvider {
            override val providerId = "test"
            override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
                if (fail) error("offline")
                return listOf(event)
            }
        }
        val registry = NavigationLiveProviderRegistry(listOf(provider))
        assertEquals(listOf(event), registry.fetch(2_000))
        fail = true
        assertEquals(listOf(event), registry.fetch(3_000))
    }
}
