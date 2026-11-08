package org.routingplatform.app.navigation

interface NavigationLiveEventProvider {
    val providerId: String
    fun fetch(nowEpochMs: Long): List<NavigationLiveEvent>
}

class ResilientLiveEventProvider(
    private val providers: List<NavigationLiveEventProvider>,
    private val maxAttemptsPerProvider: Int = 2,
) {
    init {
        require(maxAttemptsPerProvider > 0)
    }

    fun fetchAll(nowEpochMs: Long): List<NavigationLiveEvent> {
        val collected = LinkedHashMap<String, NavigationLiveEvent>()
        providers.forEach { provider ->
            repeat(maxAttemptsPerProvider) { attempt ->
                val result = runCatching { provider.fetch(nowEpochMs) }.getOrNull()
                if (result != null) {
                    result.filter { it.isValid(nowEpochMs) }.forEach { event ->
                        collected[event.id] = event
                    }
                    return@forEach
                }
                if (attempt + 1 == maxAttemptsPerProvider) return@repeat
            }
        }
        return collected.values.toList()
    }
}
