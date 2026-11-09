package org.routingplatform.app.navigation

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal class OverpassPoiProvider(
    private val endpoint: URI,
    private val locationProvider: () -> RoutePoint?,
    private val radiusMeters: Int = 1_500,
    private val timeoutMs: Int = 8_000,
) : NavigationLiveEventProvider {
    override val providerId: String = "osm-overpass"

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val point = locationProvider() ?: return emptyList()
        val query = "[out:json][timeout:12];" +
            "nwr(around:" + radiusMeters + "," + point.latitude + "," + point.longitude + ")" +
            """["amenity"~"fuel|restaurant|cafe|parking|charging_station"];out center tags;"""
        val url = URI(endpoint.toString().trimEnd('/') + "/api/interpreter?data=" +
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())).toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return emptyList()
            val elements = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("elements") ?: return emptyList()
            buildList {
                for (index in 0 until elements.length()) {
                    val item = elements.optJSONObject(index) ?: continue
                    val tags = item.optJSONObject("tags") ?: continue
                    val center = item.optJSONObject("center")
                    val lat = item.optDouble("lat", center?.optDouble("lat", Double.NaN) ?: Double.NaN)
                    val lon = item.optDouble("lon", center?.optDouble("lon", Double.NaN) ?: Double.NaN)
                    if (!lat.isFinite() || !lon.isFinite()) continue
                    val amenity = tags.optString("amenity", "poi")
                    val name = tags.optString("name", amenity)
                    add(NavigationLiveEvent(
                        id = "osm-" + item.optLong("id", index.toLong()),
                        kind = LiveEventKind.POI_UPDATE,
                        latitude = lat,
                        longitude = lon,
                        severity = 1,
                        confidence = 0.8,
                        capturedAtEpochMs = nowEpochMs,
                        validUntilEpochMs = nowEpochMs + 30 * 60 * 1_000L,
                        source = providerId + ":" + amenity + ":" + name,
                    ))
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
