package org.routingplatform.app.training

import java.util.ArrayDeque

/**
 * In-memory bounded FIFO journal with duplicate suppression. Admission is
 * checked before mutation, so the byte ceiling remains hard even when only
 * protected records remain.
 */
internal class FifoEventJournal(
    private val maxBytes: Long,
) {
    private val events = ArrayDeque<TrainingEvent>()
    private val eventIds = HashSet<String>()
    private var totalBytes = 0L

    init {
        require(maxBytes > 0L)
    }

    @Synchronized
    fun append(event: TrainingEvent): JournalAppendResult {
        if (event.eventId in eventIds) {
            return JournalAppendResult.Rejected(
                reason = RejectionReason.DUPLICATE_EVENT,
                totalBytes = totalBytes,
                eventCount = events.size,
            )
        }
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
            eventIds.remove(removable.eventId)
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
        eventIds += event.eventId
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
                this.eventIds.remove(event.eventId)
                totalBytes -= event.sizeBytes
                removed += 1
            }
        }
        return removed
    }

    @Synchronized fun sizeBytes(): Long = totalBytes
    @Synchronized fun size(): Int = events.size

    @Synchronized
    fun clear() {
        events.clear()
        eventIds.clear()
        totalBytes = 0L
    }
}
