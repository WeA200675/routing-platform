package org.routingplatform.app.navigation

/**
 * Central provider registry used by the runtime. Providers are deliberately
 * injected, so production can use network adapters while tests and offline
 * mode can use deterministic cached providers.
 */
class NavigationLiveProviderRegistry(
    providers: List<NavigationLiveEventProvider>,
    private val cache: NavigationLiveEventCache = NavigationLiveEventCache(),
) {
    private val resilient = ResilientLiveEventProvider(
        providers = providers.map { provider ->
            CircuitBreakingLiveEventProvider(
                delegate = provider,
                circuit = NavigationLiveProviderCircuit(),
            )
        },
    )

    fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val fresh = resilient.fetchAll(nowEpochMs)
        if (fresh.isNotEmpty()) {
            cache.replace(fresh)
            return NavigationLiveEventPrioritizer.prioritize(fresh, nowEpochMs).map { it.event }
        }
        return NavigationLiveEventPrioritizer
            .prioritize(cache.read(), nowEpochMs)
            .map { it.event }
    }
}

class NavigationLiveEventCache {
    @Volatile
    private var events: List<NavigationLiveEvent> = emptyList()

    fun replace(value: List<NavigationLiveEvent>) {
        events = value.toList()
    }

    fun read(): List<NavigationLiveEvent> = events
}
