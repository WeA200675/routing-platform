package org.routingplatform.app.training

import java.util.ArrayDeque

internal class FifoEventJournal(
    private val maxBytes: Long,
) {
    private val events = ArrayDeque<TrainingEvent>()
    private val eventIds = HashSet<String>()
    private var totalBytes = 0L

    init { require(maxBytes > 0L) }

    @Synchronized
    fun append(event: TrainingEvent): JournalAppendResult {
        if (event.eventId in eventIds) return rejected(RejectionReason.DUPLICATE_EVENT)
        if (event.sizeBytes.toLong() > maxBytes) return rejected(RejectionReason.EVENT_TOO_LARGE)
        val evicted = mutableListOf<String>()
        while (totalBytes + event.sizeBytes > maxBytes) {
            val removable = events.firstOrNull { !it.protected } ?: break
            events.remove(removable)
            eventIds.remove(removable.eventId)
            totalBytes -= removable.sizeBytes
            evicted += removable.eventId
        }
        if (totalBytes + event.sizeBytes > maxBytes) {
            return rejected(RejectionReason.ONLY_PROTECTED_EVENTS_REMAIN)
        }
        events.addLast(event)
        eventIds += event.eventId
        totalBytes += event.sizeBytes
        return JournalAppendResult.Accepted(evicted, totalBytes, events.size)
    }

    @Synchronized
    fun peekBatch(maxEvents: Int, maxBatchBytes: Long): List<TrainingEvent> {
        require(maxEvents > 0 && maxBatchBytes > 0L)
        val ordered = events.filter { it.protected } + events.filter { !it.protected }
        val result = mutableListOf<TrainingEvent>()
        var bytes = 0L
        for (event in ordered) {
            if (result.size >= maxEvents || bytes + event.sizeBytes > maxBatchBytes) break
            result += event
            bytes += event.sizeBytes
        }
        return result
    }

    @Synchronized
    fun snapshot(): List<TrainingEvent> = events.toList()

    @Synchronized
    fun integrity(): JournalIntegrity = FifoJournalIntegrity.calculate(events.toList())

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

    private fun rejected(reason: RejectionReason) =
        JournalAppendResult.Rejected(reason, totalBytes, events.size)
}
