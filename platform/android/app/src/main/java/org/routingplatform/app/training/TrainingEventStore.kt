package org.routingplatform.app.training

internal interface TrainingEventStore {
    fun append(event: TrainingEvent): JournalAppendResult
    fun peekBatch(maxEvents: Int, maxBatchBytes: Long): List<TrainingEvent>
    fun acknowledge(eventIds: Set<String>): Int
}
