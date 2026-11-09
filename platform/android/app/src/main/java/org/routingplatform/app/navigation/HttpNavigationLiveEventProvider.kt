package org.routingplatform.app.navigation

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small provider adapter for authenticated JSON/GeoJSON-compatible live feeds.
 * The endpoint is optional: failures return an empty list so offline routing
 * remains available and the resilient provider can try the next source.
 */
internal class HttpNavigationLiveEventProvider(
    override val providerId: String,
    private val endpoint: String,
    private val headers: Map<String, String> = emptyMap(),
    private val connectTimeoutMs: Int = 2_000,
    private val readTimeoutMs: Int = 4_000,
) : NavigationLiveEventProvider {
    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> =
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("Accept", "application/json")
                headers.forEach { (key, value) -> setRequestProperty(key, value) }
            }
            try {
                if (connection.responseCode !in 200..299) return emptyList()
                parse(connection.inputStream.bufferedReader().use { it.readText() }, nowEpochMs)
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())

    private fun parse(body: String, nowEpochMs: Long): List<NavigationLiveEvent> {
        val root = body.trim()
        val envelope = root.takeIf { it.startsWith("{") }?.let { JSONObject(it) }
        val envelopeSignature = envelope?.optString("signature_base64")?.trim()?.takeIf { it.isNotEmpty() }
        val envelopeKeyId = envelope?.optString("signature_key_id")?.trim()?.takeIf { it.isNotEmpty() }
        val array = when {
            root.startsWith("[") -> JSONArray(root)
            envelope != null -> envelope.optJSONArray("events") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val event = runCatching {
                    NavigationLiveEvent(
                        id = item.optString("id", "$providerId-$index"),
                        kind = LiveEventKind.valueOf(
                            item.optString("kind", "HAZARD").uppercase(),
                        ),
                        latitude = item.optDouble("latitude", Double.NaN),
                        longitude = item.optDouble("longitude", Double.NaN),
                        directionDegrees = item.optDouble("direction_degrees", Double.NaN)
                            .takeUnless { it.isNaN() },
                        severity = item.optInt("severity", 0).coerceIn(0, 5),
                        confidence = item.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                        capturedAtEpochMs = item.optLong("captured_at_epoch_ms", nowEpochMs),
                        validUntilEpochMs = item.optLong(
                            "valid_until_epoch_ms",
                            nowEpochMs + 60_000L,
                        ),
                        source = item.optString("source", providerId),
                        signatureBase64 = item.optString("signature_base64").trim().takeIf { it.isNotEmpty() } ?: envelopeSignature,
                        signatureKeyId = item.optString("signature_key_id").trim().takeIf { it.isNotEmpty() } ?: envelopeKeyId,
                    )
                }.getOrNull()
                if (event != null && event.isValid(nowEpochMs)) add(event)
            }
        }
    }
}
