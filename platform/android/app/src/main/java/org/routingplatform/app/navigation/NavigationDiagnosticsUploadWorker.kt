package org.routingplatform.app.navigation

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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
) {
    fun uploadNext(baseUrl: String): NavigationDiagnosticsUploadResult {
        val snapshot = queue.peek() ?: return NavigationDiagnosticsUploadResult.NothingPending
        if (!baseUrl.startsWith("https://")) {
            return NavigationDiagnosticsUploadResult.Failed("Training API muss HTTPS verwenden")
        }
        return runCatching {
            val payload = snapshot.toString()
            val connection = (URL(baseUrl.trimEnd('/') + "/api/training/events")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            if (code in 200..299) {
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
