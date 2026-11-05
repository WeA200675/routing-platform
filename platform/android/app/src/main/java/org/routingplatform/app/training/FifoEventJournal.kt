package org.routingplatform.app.training

import java.util.ArrayDeque

/**
 * In-memory bounded journal used as the first non-blocking queue boundary.
 *
 * Admission is checked before mutation. Therefore [maxBytes] can never be
 * exceeded, even when only protected records remain.
 */
internal class FifoEventJournal(
    private val maxBytes: Long,
) {
    private val events = ArrayDeque<TrainingEvent>()
    private var totalBytes = 0L

    init {
        require(maxBytes > 0L)
    }

    @Synchronized
    fun append(event: TrainingEvent): JournalAppendResult {
        if (event.sizeBytes.toLong() > maxBytes) {
            return JournalAppendResult.Rejected(
                reason = RejectionReason.EVENT_TOO_LARGE,
                totalBytes = totalBytes,
                eventCount = events.size,
            )
        }

        val evicted = mutableListOf<String>()
        while (totalBytes + event.sizeBytes > maxBytes) {
            val removable = events.firstOrNull { !it.protected } ?: break
            events.remove(removable)
            totalBytes -= removable.sizeBytes
            evicted += removable.eventId
        }

        if (totalBytes + event.sizeBytes > maxBytes) {
            return JournalAppendResult.Rejected(
                reason = RejectionReason.ONLY_PROTECTED_EVENTS_REMAIN,
                totalBytes = totalBytes,
                eventCount = events.size,
            )
        }

        events.addLast(event)
        totalBytes += event.sizeBytes
        return JournalAppendResult.Accepted(
            evictedEventIds = evicted,
            totalBytes = totalBytes,
            eventCount = events.size,
        )
    }

    @Synchronized
    fun peekBatch(maxEvents: Int, maxBatchBytes: Long): List<TrainingEvent> {
        require(maxEvents > 0)
        require(maxBatchBytes > 0L)
        val result = mutableListOf<TrainingEvent>()
        var bytes = 0L
        for (event in events) {
            if (result.size >= maxEvents || bytes + event.sizeBytes > maxBatchBytes) break
            result += event
            bytes += event.sizeBytes
        }
        return result
    }

    @Synchronized
    fun acknowledge(eventIds: Set<String>): Int {
        if (eventIds.isEmpty()) return 0
        var removed = 0
        val iterator = events.iterator()
        while (iterator.hasNext()) {
            val event = iterator.next()
            if (event.eventId in eventIds) {
                iterator.remove()
                totalBytes -= event.sizeBytes
                removed += 1
            }
        }
        return removed
    }

    @Synchronized
    fun sizeBytes(): Long = totalBytes

    @Synchronized
    fun size(): Int = events.size

    @Synchronized
    fun clear() {
        events.clear()
        totalBytes = 0L
    }
}
