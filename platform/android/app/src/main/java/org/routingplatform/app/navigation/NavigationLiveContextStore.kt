package org.routingplatform.app.navigation

import java.util.concurrent.ConcurrentHashMap

class NavigationLiveContextStore(
    private val maxEvents: Int = 512,
) {
    private val events = ConcurrentHashMap<String, NavigationLiveEvent>()

    init {
        require(maxEvents > 0)
    }

    fun upsert(event: NavigationLiveEvent): Boolean {
        if (!event.isValid(event.capturedAtEpochMs)) return false
        events[event.id] = event
        trim()
        return true
    }

    fun removeExpired(nowEpochMs: Long): Int {
        val before = events.size
        events.entries.removeIf { !it.value.isValid(nowEpochMs) }
        return before - events.size
    }

    fun prioritized(nowEpochMs: Long): List<LiveEventDecision> {
        removeExpired(nowEpochMs)
        return NavigationLiveEventPrioritizer.prioritize(events.values.toList(), nowEpochMs)
    }

    fun size(): Int = events.size

    private fun trim() {
        while (events.size > maxEvents) {
            events.entries
                .minByOrNull { it.value.capturedAtEpochMs }
                ?.let { events.remove(it.key, it.value) }
                ?: break
        }
    }
}
