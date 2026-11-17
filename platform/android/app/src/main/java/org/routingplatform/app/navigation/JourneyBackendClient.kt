package org.routingplatform.app.navigation

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Read-only Journey backend status; navigation never depends on this service. */
internal data class JourneyBackendStatus(
    val reachable: Boolean,
    val recommendationsEnabled: Boolean,
    val reason: String,
) {
    companion object {
        fun offline(reason: String = "backend_unavailable") =
            JourneyBackendStatus(false, false, reason)
    }
}

internal class JourneyBackendClient(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 3_000,
) {
    init {
        val url = URL(baseUrl)
        require(url.protocol == "https") { "Journey backend requires HTTPS" }
        require(url.userInfo == null && url.query == null && url.ref == null)
        require(connectTimeoutMs in 1..10_000 && readTimeoutMs in 1..10_000)
    }

    fun status(): JourneyBackendStatus {
        val connection = runCatching {
            val url = URL(baseUrl.trimEnd('/') + "/api/journey/v1/status")
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }
        }.getOrElse { return JourneyBackendStatus.offline() }

        return try {
            if (connection.responseCode != 200) {
                JourneyBackendStatus.offline("http_${connection.responseCode}")
            } else {
                val contentType = connection.contentType.orEmpty()
                if (!contentType.startsWith("application/json")) {
                    JourneyBackendStatus.offline("invalid_content_type")
                } else {
                    val body = connection.inputStream.use { stream ->
                        val bytes = stream.readNBytes(8193)
                        if (bytes.size > 8192) return JourneyBackendStatus.offline("oversized_response")
                        bytes.toString(Charsets.UTF_8)
                    }
                    val json = JSONObject(body)
                    if (json.optString("schema_version") != "journey-backend-v1") {
                        JourneyBackendStatus.offline("schema_mismatch")
                    } else {
                        val enabled = json.optBoolean("recommendations_enabled", false)
                        val ready = json.optBoolean("routing_graph_attested", false) &&
                            json.optBoolean("poi_audit_ready", false)
                        JourneyBackendStatus(true, enabled && ready,
                            if (enabled && ready) "ready" else "degraded")
                    }
                }
            }
        } catch (_: Exception) {
            JourneyBackendStatus.offline()
        } finally {
            connection.disconnect()
        }
    }
}
