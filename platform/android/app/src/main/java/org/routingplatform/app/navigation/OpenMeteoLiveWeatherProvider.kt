package org.routingplatform.app.navigation

import java.net.HttpURLConnection
import java.net.URI

class OpenMeteoLiveWeatherProvider(
    private val endpoint: URI,
    private val locationProvider: () -> RoutePoint?,
    private val timeoutMs: Int = 5_000,
) : NavigationLiveEventProvider {
    override val providerId: String = "open-meteo"

    override fun fetch(nowEpochMs: Long): List<NavigationLiveEvent> {
        val point = locationProvider() ?: return emptyList()
        val query = "latitude=${point.latitude}&longitude=${point.longitude}&current=weather_code,wind_speed_10m,visibility"
        val url = URI("${endpoint.toString().trimEnd('/')}/v1/forecast?$query").toURL()
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) error("Open-Meteo HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val code = regexNumber(body, "\"weather_code\"\\s*:\\s*([0-9]+)") ?: return emptyList()
            val wind = regexNumber(body, "\"wind_speed_10m\"\\s*:\\s*([0-9.]+)") ?: 0.0
            val visibility = regexNumber(body, "\"visibility\"\\s*:\\s*([0-9.]+)") ?: 10_000.0
            val severity = when {
                code >= 95 || wind >= 80.0 -> 5
                code >= 80 || wind >= 55.0 || visibility < 1_000.0 -> 4
                code >= 70 || wind >= 35.0 || visibility < 3_000.0 -> 3
                code >= 50 || wind >= 20.0 || visibility < 5_000.0 -> 2
                else -> 1
            }
            if (severity < 2) return emptyList()
            listOf(
                NavigationLiveEvent(
                    id = "weather-$providerId-${point.latitude}-${point.longitude}",
                    kind = LiveEventKind.WEATHER,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    severity = severity,
                    confidence = 0.85,
                    capturedAtEpochMs = nowEpochMs,
                    validUntilEpochMs = nowEpochMs + 15 * 60 * 1_000L,
                    source = providerId,
                )
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun regexNumber(body: String, pattern: String): Double? =
        Regex(pattern).find(body)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}
