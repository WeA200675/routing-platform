package org.routingplatform.app.training

import java.security.MessageDigest

internal data class JournalIntegrity(
    val eventCount: Int,
    val totalBytes: Long,
    val digest: String,
)

internal object FifoJournalIntegrity {
    fun calculate(events: List<TrainingEvent>): JournalIntegrity {
        val canonical = events.joinToString("\n") { event ->
            listOf(
                event.eventId,
                event.schemaVersion,
                event.eventType,
                event.capturedAtEpochMs,
                event.source,
                event.qualityScore,
                event.payload,
                event.sizeBytes,
                event.protected,
            ).joinToString("|")
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return JournalIntegrity(
            eventCount = events.size,
            totalBytes = events.sumOf { it.sizeBytes.toLong() },
            digest = digest,
        )
    }

    fun verify(events: List<TrainingEvent>, expected: JournalIntegrity): Boolean {
        val actual = calculate(events)
        return actual == expected
    }
}
