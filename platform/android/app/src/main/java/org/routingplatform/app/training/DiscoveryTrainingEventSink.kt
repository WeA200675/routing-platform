package org.routingplatform.app.training

import org.routingplatform.app.intelligence.DiscoveryTrainingRecord

internal class DiscoveryTrainingEventSink(
    private val bridge: TrainingRuntimeBridge,
) {
    fun record(record: DiscoveryTrainingRecord): JournalAppendResult =
        bridge.recordEvent(
            TrainingEvent(
                eventId = record.eventId,
                schemaVersion = "discovery-training-v1",
                eventType = "discovery.provider-observation",
                sessionId = null,
                routeId = null,
                capturedAtEpochMs = record.capturedAtEpochMs,
                source = "android.discovery",
                qualityScore = record.score.coerceIn(0.0, 1.0),
                payload = buildPayload(record),
                sizeBytes = 512,
            ),
        )

    private fun buildPayload(record: DiscoveryTrainingRecord): String = buildString {
        append("{\"candidate_id\":\"").append(escape(record.candidateId))
        append("\",\"kind\":\"").append(record.kind)
        append("\",\"stage\":\"").append(record.stage)
        append("\",\"score\":").append(record.score)
        append(",\"reputation\":").append(record.reputation)
        append(",\"reliability\":").append(record.reliability)
        append(",\"freshness\":").append(record.freshness)
        append(",\"safety\":").append(record.safety)
        append(",\"latency_ms\":").append(record.latencyMs)
        append(",\"success\":").append(record.success)
        append(",\"reason\":\"").append(escape(record.reason))
        append("\"}")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace(""", "\"")
}
