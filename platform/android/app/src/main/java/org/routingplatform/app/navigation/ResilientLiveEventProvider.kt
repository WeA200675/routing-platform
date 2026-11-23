package org.routingplatform.app.navigation

interface NavigationLiveEventProvider {
    val providerId: String
    fun fetch(nowEpochMs: Long): List<NavigationLiveEvent>
}

/**
 * Optional network enrichment only. Provider failures must not propagate to
 * deterministic offline navigation. Circuit state survives refresh calls.
 */
class ResilientLiveEventProvider(
    private val providers: List<NavigationLiveEventProvider>,
    private val maxAttemptsPerProvider: Int = 2,
    private val circuit: NavigationLiveProviderCircuit = NavigationLiveProviderCircuit(),
) {
    init {
        require(maxAttemptsPerProvider > 0)
    }

    fun fetchAll(nowEpochMs: Long): List<NavigationLiveEvent> {
        val collected = LinkedHashMap<String, NavigationLiveEvent>()
        providers.forEach { provider ->
            if (!circuit.isAllowed(provider.providerId, nowEpochMs)) return@forEach
            var succeeded = false
            for (attempt in 0 until maxAttemptsPerProvider) {
                val result = runCatching { provider.fetch(nowEpochMs) }.getOrNull()
                if (result != null) {
                    // A successful empty response is still a healthy provider.
                    circuit.recordSuccess(provider.providerId)
                    result.filter { it.isValid(nowEpochMs) }.forEach { event ->
                        collected[event.id] = event
                    }
                    succeeded = true
                    break
                }
            }
            // Count one failed refresh, not each immediate retry, towards cooldown.
            if (!succeeded) circuit.recordFailure(provider.providerId, nowEpochMs)
        }
        return collected.values.toList()
    }

    fun providerHealth(providerId: String): ProviderHealth = circuit.health(providerId)
}
