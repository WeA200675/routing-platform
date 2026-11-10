package org.routingplatform.app.training

data class TrainingEvent(
    val eventId: String,
    val schemaVersion: String,
    val eventType: String,
    val sessionId: String?,
    val routeId: String?,
    val capturedAtEpochMs: Long,
    val source: String,
    val qualityScore: Double,
    val payload: String,
    val sizeBytes: Int,
    val protected: Boolean = false,
) {
    init {
        require(eventId.isNotBlank())
        require(schemaVersion.isNotBlank())
        require(eventType.isNotBlank())
        require(source.isNotBlank())
        require(capturedAtEpochMs >= 0L)
        require(qualityScore in 0.0..1.0)
        require(sizeBytes >= 0)
    }
}

internal sealed interface JournalAppendResult {
    data class Accepted(
        val evictedEventIds: List<String>,
        val totalBytes: Long,
        val eventCount: Int,
    ) : JournalAppendResult

    data class Rejected(
        val reason: RejectionReason,
        val totalBytes: Long,
        val eventCount: Int,
    ) : JournalAppendResult
}

internal enum class RejectionReason {
    EVENT_TOO_LARGE,
    DUPLICATE_EVENT,
    ONLY_PROTECTED_EVENTS_REMAIN,
}
