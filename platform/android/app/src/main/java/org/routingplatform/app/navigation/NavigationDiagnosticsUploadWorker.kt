package org.routingplatform.app.navigation

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject
import org.json.JSONArray

sealed interface NavigationDiagnosticsUploadResult {
    data object NothingPending : NavigationDiagnosticsUploadResult
    data class Uploaded(val bytes: Int) : NavigationDiagnosticsUploadResult
    data class Failed(val reason: String) : NavigationDiagnosticsUploadResult
}

/**
 * Uploads one durable diagnostics package per invocation. It never removes a
 * package unless the HTTPS endpoint returns a successful 2xx response.
 */
class NavigationDiagnosticsUploadWorker(
    private val queue: NavigationDiagnosticsUploadQueue,
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000,
    private val bearerToken: String? = null,
) {
    private fun receiptsMatch(events: JSONArray, receipts: JSONArray?): Boolean {
        if (receipts == null || receipts.length() != events.length()) return false
        val ids = (0 until events.length()).map { events.optJSONObject(it)?.optString("event_id") ?: return false }
        if (ids.any { it.isBlank() } || ids.toSet().size != ids.size) return false
        val received = (0 until receipts.length()).map { index ->
            val item = receipts.optJSONObject(index) ?: return false
            if (item.optString("status") !in setOf("accepted", "duplicate")) return false
            if (!item.optString("sha256").matches(Regex("[0-9a-f]{64}"))) return false
            item.optString("event_id")
        }
        return received.toSet() == ids.toSet() && received.size == received.toSet().size
    }

    fun uploadNext(baseUrl: String): NavigationDiagnosticsUploadResult {
        val snapshot = queue.peek() ?: return NavigationDiagnosticsUploadResult.NothingPending
        if (!baseUrl.startsWith("https://")) {
            return NavigationDiagnosticsUploadResult.Failed("Training API muss HTTPS verwenden")
        }
        return runCatching {
            val events = snapshot.optJSONArray("events")
                ?: return NavigationDiagnosticsUploadResult.Failed("Diagnostics snapshot has no events")
            if (events.length() == 0 || events.length() > 256) return NavigationDiagnosticsUploadResult.Failed("Invalid diagnostics batch size")
            val payload = JSONObject().put("events", events).toString()
            if (payload.toByteArray(StandardCharsets.UTF_8).size > 256 * 1024) return NavigationDiagnosticsUploadResult.Failed("Diagnostics batch too large")
            val normalizedBase = baseUrl.trimEnd('/').removeSuffix("/api")
            val connection = (URL(normalizedBase + "/api/training/events")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                bearerToken?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val response = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            val ack = runCatching { JSONObject(response) }.getOrNull()
            if (code == 202 && ack?.optBoolean("durable", false) == true &&
                ack.optInt("accepted", -1) + ack.optInt("duplicates", -1) == events.length() &&
                receiptsMatch(events, ack.optJSONArray("receipts"))) {
                if (!queue.acknowledge(snapshot)) return NavigationDiagnosticsUploadResult.Failed("Outbox ACK mismatch")
                NavigationDiagnosticsUploadResult.Uploaded(payload.length)
            } else {
                NavigationDiagnosticsUploadResult.Failed("HTTP $code")
            }
        }.getOrElse { error ->
            NavigationDiagnosticsUploadResult.Failed(error.message ?: "upload failed")
        }
    }
}
