package org.routingplatform.app.training

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Resolves the current cloud endpoint without ever needing SSH credentials. */
internal data class TrainingEndpoint(
    val dashboardUrl: String,
    val apiUrl: String,
    val source: EndpointSource,
)

internal enum class EndpointSource { DISCOVERY, CACHED, FALLBACK }

internal class TrainingEndpointDiscovery(
    context: Context,
    private val discoveryUrl: String,
    private val fallbackUrl: String? = null,
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000,
) {
    private val preferences = context.getSharedPreferences("training-endpoint", Context.MODE_PRIVATE)

    fun resolve(): TrainingEndpoint {
        val candidates = buildList {
            add(discoveryUrl)
            preferences.getString(KEY_DASHBOARD, null)?.let(::add)
            fallbackUrl?.let(::add)
        }.distinct()

        candidates.forEachIndexed { index, candidate ->
            val endpoint = runCatching { fetch(candidate, index == 0) }.getOrNull() ?: return@forEachIndexed
            preferences.edit()
                .putString(KEY_DASHBOARD, endpoint.dashboardUrl)
                .putString(KEY_API, endpoint.apiUrl)
                .apply()
            return endpoint
        }

        val cached = preferences.getString(KEY_DASHBOARD, null)
        if (cached != null) {
            return TrainingEndpoint(cached, preferences.getString(KEY_API, cached) ?: cached, EndpointSource.CACHED)
        }
        if (fallbackUrl != null) return TrainingEndpoint(fallbackUrl, fallbackUrl, EndpointSource.FALLBACK)
        throw IllegalStateException("Keine Trainingsverbindung erreichbar")
    }

    private fun fetch(candidate: String, discoveryCandidate: Boolean): TrainingEndpoint {
        val connection = (URL(candidate).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val dashboard = if (discoveryCandidate) jsonValue(body, "dashboard_url") else candidate
            val api = if (discoveryCandidate) jsonValue(body, "api_url") ?: "$dashboard/api" else "$candidate/api"
            require(!dashboard.isNullOrBlank() && dashboard.startsWith("https://")) { "Unsichere Trainingsadresse" }
            return TrainingEndpoint(dashboard.trimEnd('/'), api!!.trimEnd('/'), if (discoveryCandidate) EndpointSource.DISCOVERY else EndpointSource.FALLBACK)
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonValue(body: String, key: String): String? =
        Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(body)?.groupValues?.get(1)

    private companion object {
        const val KEY_DASHBOARD = "dashboard_url"
        const val KEY_API = "api_url"
    }
}
