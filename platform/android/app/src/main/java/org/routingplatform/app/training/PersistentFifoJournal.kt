package org.routingplatform.app.training

internal interface FifoJournalPersistence {
    fun load(): PersistedJournal?
    fun save(events: List<TrainingEvent>, integrity: JournalIntegrity)
}

internal data class PersistedJournal(
    val events: List<TrainingEvent>,
    val integrity: JournalIntegrity,
)

internal class PersistentFifoJournal(
    private val maxBytes: Long,
    private val persistence: FifoJournalPersistence,
) : TrainingEventStore {
    private val journal = FifoEventJournal(maxBytes)

    init {
        persistence.load()?.let { persisted ->
            if (FifoJournalIntegrity.verify(persisted.events, persisted.integrity)) {
                persisted.events.forEach { journal.append(it) }
            }
        }
    }

    @Synchronized
    override fun append(event: TrainingEvent): JournalAppendResult =
        journal.append(event).also { persist() }

    @Synchronized
    override fun peekBatch(maxEvents: Int, maxBatchBytes: Long): List<TrainingEvent> =
        journal.peekBatch(maxEvents, maxBatchBytes)

    @Synchronized
    override fun acknowledge(eventIds: Set<String>): Int =
        journal.acknowledge(eventIds).also { persist() }

    @Synchronized
    fun integrity(): JournalIntegrity = journal.integrity()

    private fun persist() {
        persistence.save(journal.snapshot(), journal.integrity())
    }
}
