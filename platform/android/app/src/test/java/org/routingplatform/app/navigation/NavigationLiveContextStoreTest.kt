package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLiveContextStoreTest {
    @Test
    fun replacesDuplicateAndExpiresEvents() {
        val store = NavigationLiveContextStore(maxEvents = 4)
        assertTrue(store.upsert(event("a", validUntil = 2_000)))
        assertTrue(store.upsert(event("a", validUntil = 3_000)))
        assertEquals(1, store.size())
        assertEquals(0, store.removeExpired(2_500))
        assertEquals(1, store.size())
    }

    @Test
    fun keepsStoreBoundedByRemovingOldestEvents() {
        val store = NavigationLiveContextStore(maxEvents = 2)
        store.upsert(event("a", capturedAt = 1))
        store.upsert(event("b", capturedAt = 2))
        store.upsert(event("c", capturedAt = 3))
        assertEquals(2, store.size())
        assertEquals(setOf("b", "c"), store.prioritized(4).map { it.event.id }.toSet())
    }

    private fun event(
        id: String,
        capturedAt: Long = 1_000,
        validUntil: Long = 10_000,
    ) = NavigationLiveEvent(
        id = id,
        kind = LiveEventKind.TRAFFIC_JAM,
        latitude = 48.0,
        longitude = 11.0,
        severity = 2,
        confidence = 0.9,
        capturedAtEpochMs = capturedAt,
        validUntilEpochMs = validUntil,
        source = "test",
    )
}
