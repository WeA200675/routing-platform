package org.routingplatform.app.navigation

/**
 * Keeps the last valid provider response for a bounded time. This prevents a
 * short network outage from erasing known hazards while still expiring stale
 * traffic data deterministically.
 */
internal class CachedNavigationLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val maxAgeMs: Long = 15 * 60 * 1_000L,
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
) : NavigationLiveEventProvider {
    init {
        require(maxAgeMs in 1_000L..86_400_000L)
    }

    override val providerId: String = delegate.providerId

    @Volatile
    private var cached: CacheEntry? = null

    @Volatile
    var lastError: String? = null
        private set

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val now = nowEpochMs.coerceAtLeast(clockEpochMs())
        val fresh = runCatching { delegate.fetch(now) }
            .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            .getOrNull()

        if (fresh != null) {
            val valid = fresh.filter { it.isValid(now) }
            if (valid.isNotEmpty()) {
                cached = CacheEntry(now, valid)
                lastError = null
                return valid
            }
        }

        val entry = cached
        if (entry != null && now - entry.capturedAtEpochMs <= maxAgeMs) {
            return entry.events.filter { it.isValid(now) }
        }
        return emptyList()
    }

    private data class CacheEntry(
        val capturedAtEpochMs: Long,
        val events: List<NavigationLiveEvent>,
    )
}
