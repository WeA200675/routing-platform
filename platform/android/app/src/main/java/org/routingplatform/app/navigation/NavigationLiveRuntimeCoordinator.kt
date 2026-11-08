package org.routingplatform.app.navigation

data class NavigationLiveRuntimeSnapshot(
    val capturedAtEpochMs: Long,
    val prioritizedEvents: List<LiveEventDecision>,
    val providerCount: Int,
    val eventCount: Int,
)

class NavigationLiveRuntimeCoordinator(
    private val provider: ResilientLiveEventProvider,
    private val store: NavigationLiveContextStore = NavigationLiveContextStore(),
) {
    fun refresh(nowEpochMs: Long): NavigationLiveRuntimeSnapshot {
        val incoming = provider.fetchAll(nowEpochMs)
        incoming.forEach(store::upsert)
        val prioritized = store.prioritized(nowEpochMs)
        return NavigationLiveRuntimeSnapshot(
            capturedAtEpochMs = nowEpochMs,
            prioritizedEvents = prioritized,
            providerCount = incoming.map { it.source }.distinct().size,
            eventCount = prioritized.size,
        )
    }

    fun current(nowEpochMs: Long): NavigationLiveRuntimeSnapshot {
        val prioritized = store.prioritized(nowEpochMs)
        return NavigationLiveRuntimeSnapshot(
            capturedAtEpochMs = nowEpochMs,
            prioritizedEvents = prioritized,
            providerCount = prioritized.map { it.event.source }.distinct().size,
            eventCount = prioritized.size,
        )
    }
}
