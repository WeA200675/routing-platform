package org.routingplatform.app.vehicle

import org.json.JSONObject
import org.routingplatform.app.navigation.NavigationPoiRecommendation
import java.net.HttpURLConnection
import java.net.URI

class TankerkoenigPriceProvider(
    private val apiKey: String,
    private val endpoint: URI = URI("https://creativecommons.tankerkoenig.de/json/list.php"),
    private val timeoutMs: Int = 6_000,
) : EnergyPriceAvailabilityProvider {
    override fun lookup(
        poi: NavigationPoiRecommendation,
        kind: EnergyStopKind,
    ): EnergyPriceAvailability? {
        if (kind != EnergyStopKind.FUEL || apiKey.isBlank()) return null
        val url = URI(
            endpoint.toString() +
                "?lat=" + poi.event.latitude +
                "&lng=" + poi.event.longitude +
                "&rad=5&type=all&sort=dist&apikey=" + apiKey,
        ).toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            if (!json.optBoolean("ok", false)) return null
            val stations = json.optJSONArray("stations") ?: return null
            val first = stations.optJSONObject(0) ?: return null
            val price = listOf("e10", "diesel", "e5")
                .mapNotNull { first.optDouble(it, Double.NaN).takeIf { value -> value.isFinite() } }
                .minOrNull()
            EnergyPriceAvailability(
                pricePerUnit = price,
                available = first.optBoolean("isOpen", true),
                capturedAtEpochMs = System.currentTimeMillis(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
