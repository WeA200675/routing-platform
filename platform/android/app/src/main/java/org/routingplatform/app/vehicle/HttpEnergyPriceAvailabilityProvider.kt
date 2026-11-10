package org.routingplatform.app.vehicle

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.routingplatform.app.navigation.NavigationPoiRecommendation

class HttpEnergyPriceAvailabilityProvider(
    private val endpoint: URI,
    private val timeoutMs: Int = 6_000,
) : EnergyPriceAvailabilityProvider {
    override fun lookup(
        poi: NavigationPoiRecommendation,
        kind: EnergyStopKind,
    ): EnergyPriceAvailability? {
        val query = "lat=" + poi.event.latitude +
            "&lon=" + poi.event.longitude +
            "&kind=" + URLEncoder.encode(kind.name, StandardCharsets.UTF_8.toString())
        val connection = (URI(endpoint.toString() + "?" + query).toURL().openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            EnergyPriceAvailability(
                pricePerUnit = json.optDouble("price_per_unit", Double.NaN).takeIf { it.isFinite() },
                available = if (json.has("available")) json.optBoolean("available") else null,
                capturedAtEpochMs = json.optLong("captured_at_epoch_ms", System.currentTimeMillis()),
            )
        } finally {
            connection.disconnect()
        }
    }
}
