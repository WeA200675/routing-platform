package org.routingplatform.app.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FifoEventJournalTest {
    @Test
    fun appendEvictsOldestEligibleEventBeforeCrossingHardLimit() {
        val journal = FifoEventJournal(maxBytes = 10)
        assertIs<JournalAppendResult.Accepted>(journal.append(event("a", 4)))
        assertIs<JournalAppendResult.Accepted>(journal.append(event("b", 4)))
        val result = assertIs<JournalAppendResult.Accepted>(journal.append(event("c", 6)))

        assertEquals(listOf("a"), result.evictedEventIds)
        assertEquals(10L, journal.sizeBytes())
        assertEquals(listOf("b", "c"), journal.peekBatch(10, 100).map { it.eventId })
    }

    @Test
    fun protectedEventsAreNeverEvictedAndOversizedAdmissionIsRejected() {
        val journal = FifoEventJournal(maxBytes = 10)
        assertIs<JournalAppendResult.Accepted>(
            journal.append(event("protected", 6, protected = true)),
        )
        val result = assertIs<JournalAppendResult.Rejected>(
            journal.append(event("new", 5)),
        )

        assertEquals(RejectionReason.ONLY_PROTECTED_EVENTS_REMAIN, result.reason)
        assertEquals(6L, journal.sizeBytes())
        assertEquals(listOf("protected"), journal.peekBatch(10, 100).map { it.eventId })
    }

    @Test
    fun eventLargerThanJournalIsRejectedBeforeMutation() {
        val journal = FifoEventJournal(maxBytes = 10)
        val result = assertIs<JournalAppendResult.Rejected>(
            journal.append(event("too-large", 11)),
        )

        assertEquals(RejectionReason.EVENT_TOO_LARGE, result.reason)
        assertEquals(0, journal.size())
        assertTrue(journal.peekBatch(1, 10).isEmpty())
    }

    @Test
    fun acknowledgementRemovesOnlyRequestedEvents() {
        val journal = FifoEventJournal(maxBytes = 20)
        journal.append(event("a", 3))
        journal.append(event("b", 4))
        journal.append(event("c", 5))

        assertEquals(2, journal.acknowledge(setOf("a", "c")))
        assertEquals(4L, journal.sizeBytes())
        assertEquals(listOf("b"), journal.peekBatch(10, 100).map { it.eventId })
    }

    private fun event(
        id: String,
        size: Int,
        protected: Boolean = false,
    ) = TrainingEvent(
        eventId = id,
        schemaVersion = "performance-v1",
        eventType = "navigation.performance",
        sessionId = "session",
        routeId = "route",
        capturedAtEpochMs = 1L,
        source = "android",
        qualityScore = 1.0,
        payload = "{}",
        sizeBytes = size,
        protected = protected,
    )
}
