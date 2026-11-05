package org.routingplatform.app.training

internal class TrainingRuntimeBridge(
    private val telemetry: NavigationPerformanceTelemetry,
    private val journal: FifoEventJournal,
    private val apiClient: TrainingCenterApiClient,
) {
    fun record(sampleInput: PerformanceSampleInput): JournalAppendResult? {
        val sample = telemetry.record(
            snapshot = sampleInput.snapshot,
            capturedAtElapsedMs = sampleInput.capturedAtElapsedMs,
            voiceStage = sampleInput.voiceStage,
            fallbackUsed = sampleInput.fallbackUsed,
        ) ?: return null
        val event = TrainingEvent(
            eventId = sampleInput.eventId,
            schemaVersion = sample.schemaVersion,
            eventType = "navigation.performance",
            sessionId = sample.sessionId,
            routeId = sample.routeId,
            capturedAtEpochMs = sampleInput.capturedAtElapsedMs,
            source = "android.navigation",
            qualityScore = if (sample.fallbackUsed) 0.6 else 1.0,
            payload = sample.toString(),
            sizeBytes = sampleInput.sizeBytes,
        )
        return journal.append(event)
    }

    fun flush(maxEvents: Int = 32, maxBatchBytes: Long = 64 * 1024): UploadResult {
        val batch = journal.peekBatch(maxEvents, maxBatchBytes)
        if (batch.isEmpty()) return UploadResult.NothingToUpload
        val result = apiClient.upload(batch)
        if (result is UploadResult.Uploaded) {
            journal.acknowledge(batch.map { it.eventId }.toSet())
        }
        return result
    }
}

internal data class PerformanceSampleInput(
    val eventId: String,
    val snapshot: org.routingplatform.app.navigation.NavigationUiSnapshot,
    val capturedAtElapsedMs: Long,
    val voiceStage: org.routingplatform.app.ui.NavigationVoiceCueStage? = null,
    val fallbackUsed: Boolean = false,
    val sizeBytes: Int = 512,
)
