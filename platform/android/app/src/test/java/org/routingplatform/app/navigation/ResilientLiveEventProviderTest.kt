package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class ResilientLiveEventProviderTest {
    @Test
    fun retriesFailedProviderAndUsesFallbackProvider() {
        val calls = mutableMapOf<String, Int>()
        val failing = provider("failing") {
            calls["failing"] = (calls["failing"] ?: 0) + 1
            error("offline")
        }
        val fallback = provider("fallback") {
            calls["fallback"] = (calls["fallback"] ?: 0) + 1
            listOf(event("hazard"))
        }

        val result = ResilientLiveEventProvider(listOf(failing, fallback), 2)
            .fetchAll(1_000)

        assertEquals(listOf("hazard"), result.map { it.id })
        assertEquals(2, calls["failing"])
        assertEquals(1, calls["fallback"])
    }

    private fun provider(
        id: String,
        fetcher: () -> List<NavigationLiveEvent>,
    ) = object : NavigationLiveEventProvider {
        override val providerId: String = id
        override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> = fetcher()
    }

    private fun event(id: String) = NavigationLiveEvent(
        id = id,
        kind = LiveEventKind.HAZARD,
        latitude = 48.0,
        longitude = 11.0,
        severity = 5,
        confidence = 1.0,
        capturedAtEpochMs = 0,
        validUntilEpochMs = 10_000,
        source = "test",
    )
}
