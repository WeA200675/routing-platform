package org.routingplatform.app.navigation

data class NavigationLiveProviderHealth(
    val providerId: String,
    val status: Status,
    val lastSuccessEpochMs: Long?,
    val lastError: String?,
    val cacheAgeMs: Long?,
) {
    enum class Status { NEVER_CONNECTED, ONLINE, CACHED, OFFLINE }
}

/**
 * Decorator exposing measurable provider health without changing the provider
 * contract used by route scoring.
 */
internal class ObservableNavigationLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
) : NavigationLiveEventProvider {
    override val providerId: String = delegate.providerId

    @Volatile
    private var lastSuccessEpochMs: Long? = null

    @Volatile
    private var lastError: String? = null

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val result = runCatching { delegate.fetch(nowEpochMs) }
        result.onSuccess {
            if (it.isNotEmpty()) {
                lastSuccessEpochMs = nowEpochMs
                lastError = null
            }
        }.onFailure {
            lastError = it.message ?: it.javaClass.simpleName
        }
        return result.getOrDefault(emptyList())
    }

    fun health(): NavigationLiveProviderHealth {
        val success = lastSuccessEpochMs
        val age = success?.let { (clockEpochMs() - it).coerceAtLeast(0L) }
        val cached = delegate is CachedNavigationLiveEventProvider &&
            delegate.lastError != null &&
            success != null
        return NavigationLiveProviderHealth(
            providerId = providerId,
            status = when {
                success == null && lastError == null -> NavigationLiveProviderHealth.Status.NEVER_CONNECTED
                lastError == null -> NavigationLiveProviderHealth.Status.ONLINE
                cached -> NavigationLiveProviderHealth.Status.CACHED
                else -> NavigationLiveProviderHealth.Status.OFFLINE
            },
            lastSuccessEpochMs = success,
            lastError = lastError ?: (delegate as? CachedNavigationLiveEventProvider)?.lastError,
            cacheAgeMs = age,
        )
    }
}
