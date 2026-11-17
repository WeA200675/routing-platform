package org.routingplatform.app.navigation

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

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
    fun uploadNext(baseUrl: String): NavigationDiagnosticsUploadResult {
        val snapshot = queue.peek() ?: return NavigationDiagnosticsUploadResult.NothingPending
        if (!baseUrl.startsWith("https://")) {
            return NavigationDiagnosticsUploadResult.Failed("Training API muss HTTPS verwenden")
        }
        return runCatching {
            val events = snapshot.optJSONArray("events")
                ?: return NavigationDiagnosticsUploadResult.Failed("Diagnostics snapshot has no events")
            if (events.length() == 0) return NavigationDiagnosticsUploadResult.Failed("Empty diagnostics snapshot")
            val payload = JSONObject().put("events", events).toString()
            val connection = (URL(baseUrl.trimEnd('/') + "/api/training/events")
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
                ack.optInt("accepted", 0) + ack.optInt("duplicates", 0) == events.length()) {
                queue.acknowledgeFirst()
                NavigationDiagnosticsUploadResult.Uploaded(payload.length)
            } else {
                NavigationDiagnosticsUploadResult.Failed("HTTP $code")
            }
        }.getOrElse { error ->
            NavigationDiagnosticsUploadResult.Failed(error.message ?: "upload failed")
        }
    }
}
