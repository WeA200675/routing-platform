package org.routingplatform.app.navigation

class CircuitBreakingLiveEventProvider(
    private val delegate: NavigationLiveEventProvider,
    private val circuit: NavigationLiveProviderCircuit,
) : NavigationLiveEventProvider {
    override val providerId: String
        get() = delegate.providerId

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        if (!circuit.isAllowed(providerId, nowEpochMs)) return emptyList()
        return runCatching {
            delegate.fetch(nowEpochMs).also { circuit.recordSuccess(providerId) }
        }.getOrElse {
            circuit.recordFailure(providerId, nowEpochMs)
            emptyList()
        }
    }
}
