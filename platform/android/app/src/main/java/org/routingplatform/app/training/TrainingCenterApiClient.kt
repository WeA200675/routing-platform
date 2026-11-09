package org.routingplatform.app.training

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal class TrainingCenterApiClient(
    baseUrl: String,
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000,
) {
    @Volatile
    private var activeBaseUrl: String = baseUrl

    fun updateBaseUrl(url: String) {
        require(url.startsWith("https://")) { "Training API muss HTTPS verwenden" }
        activeBaseUrl = url.trimEnd('/')
    }

    fun upload(events: List<TrainingEvent>): UploadResult {
        if (events.isEmpty()) return UploadResult.NothingToUpload
        return runCatching {
            val connection = URL(activeBaseUrl.trimEnd('/') + "/api/training/events")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val payload = buildPayload(events)
            connection.outputStream.use {
                it.write(payload.toByteArray(StandardCharsets.UTF_8))
            }
            val code = connection.responseCode
            if (code in 200..299) UploadResult.Uploaded(events.size)
            else UploadResult.Failed("HTTP " + code)
        }.getOrElse { UploadResult.Failed(it.message ?: "upload failed") }
    }

    private fun buildPayload(events: List<TrainingEvent>): String {
        val body = events.joinToString(",") { event ->
            "{" +
                "\"event_id\":" + jsonString(event.eventId) +
                ",\"schema_version\":" + jsonString(event.schemaVersion) +
                ",\"event_type\":" + jsonString(event.eventType) +
                ",\"session_id\":" + (event.sessionId?.let(::jsonString) ?: "null") +
                ",\"route_id\":" + (event.routeId?.let(::jsonString) ?: "null") +
                ",\"captured_at_epoch_ms\":" + event.capturedAtEpochMs +
                ",\"source\":" + jsonString(event.source) +
                ",\"quality_score\":" + event.qualityScore +
                ",\"payload\":" + jsonString(event.payload) +
                ",\"size_bytes\":" + event.sizeBytes +
                ",\"protected\":" + event.protected +
                "}"
        }
        return "{\"schema_version\":\"training-events-v1\",\"events\":[" + body + "]}"
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""
}

internal sealed interface UploadResult {
    data object NothingToUpload : UploadResult
    data class Uploaded(val count: Int) : UploadResult
    data class Failed(val reason: String) : UploadResult
}
