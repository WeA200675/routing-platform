package org.routingplatform.app.navigation

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** Sends bounded shadow decisions to the VM; failures are deliberately fail-closed. */
internal class HttpCloudTrainingShadow(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 3_000,
) : CloudTrainingShadow {
    override fun evaluate(
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
    ): NavigationDecisionEnvelope? = runCatching {
        val normalizedBase = baseUrl.trimEnd('/')
        val endpoint = if (normalizedBase.endsWith("/api")) {
            normalizedBase + "/navigation/decision"
        } else {
            normalizedBase + "/api/navigation/decision"
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        val body = "{" +
            "\"event_id\":\"${escape(local.eventId)}\"," +
            "\"session_id\":\"${escape(twin.sessionId)}\"," +
            "\"route_version\":${twin.routeVersion}" +
            "}"
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        if (connection.responseCode !in 200..299) return null
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val origin = json(response, "origin") ?: return null
        if (origin != "CLOUD") return null
        NavigationDecisionEnvelope(
            eventId = json(response, "event_id") ?: return null,
            sessionId = json(response, "session_id") ?: return null,
            routeVersion = json(response, "route_version")?.toLongOrNull() ?: return null,
            capturedAtEpochMs = json(response, "captured_at")?.toLongOrNull() ?: return null,
            origin = AiDecisionOrigin.CLOUD,
            mode = AiDecisionMode.ADVISE,
            kind = AiDecisionKind.valueOf(json(response, "kind") ?: "WAIT_FOR_EVIDENCE"),
            confidence = json(response, "confidence")?.toDoubleOrNull() ?: 0.0,
            evidence = setOf("cloud-shadow-received"),
            expiresAtEpochMs = json(response, "expires_at_epoch_ms")?.toLongOrNull() ?: return null,
            safetyClass = json(response, "safety_class")?.toIntOrNull() ?: 1,
            modelVersion = json(response, "model_version") ?: "training-shadow-v1",
        )
    }.getOrNull()

    override fun recordOutcome(
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
        cloud: NavigationDecisionEnvelope?,
        accepted: NavigationDecisionEnvelope?,
        rejection: DecisionRejection?,
    ) = Unit

    private fun json(body: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([^,}]+))")
            .find(body)?.let { it.groupValues[1].ifEmpty { it.groupValues[2].trim() } }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
