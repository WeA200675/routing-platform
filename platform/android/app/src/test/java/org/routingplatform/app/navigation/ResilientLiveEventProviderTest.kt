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


    @Test
    fun failingProviderIsSuspendedWithoutBlockingHealthyFallback() {
        var brokenCalls = 0
        var healthyCalls = 0
        val broken = provider("broken") {
            brokenCalls++
            error("offline")
        }
        val healthy = provider("healthy") {
            healthyCalls++
            listOf(event("safe"))
        }
        val resilient = ResilientLiveEventProvider(
            listOf(broken, healthy),
            maxAttemptsPerProvider = 2,
            circuit = NavigationLiveProviderCircuit(failureThreshold = 2, suspensionMs = 100),
        )
        resilient.fetchAll(1_000)
        resilient.fetchAll(1_001)
        val duringCooldown = resilient.fetchAll(1_050)
        assertEquals(listOf("safe"), duringCooldown.map { it.id })
        assertEquals(4, brokenCalls)
        assertEquals(3, healthyCalls)
        assertEquals(2, resilient.providerHealth("broken").consecutiveFailures)
        resilient.fetchAll(1_101)
        assertEquals(6, brokenCalls)
        assertEquals(1, resilient.providerHealth("broken").consecutiveFailures)
    }

    @Test
    fun emptySuccessfulResponseResetsCircuit() {
        var fail = true
        val resilient = ResilientLiveEventProvider(
            listOf(provider("live") {
                if (fail) error("unavailable")
                emptyList()
            }),
            maxAttemptsPerProvider = 1,
            circuit = NavigationLiveProviderCircuit(failureThreshold = 2, suspensionMs = 100),
        )
        resilient.fetchAll(1_000)
        fail = false
        assertEquals(emptyList<NavigationLiveEvent>(), resilient.fetchAll(1_001))
        assertEquals(0, resilient.providerHealth("live").consecutiveFailures)
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
