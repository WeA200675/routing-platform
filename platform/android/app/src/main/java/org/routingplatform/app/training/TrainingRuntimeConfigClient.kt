package org.routingplatform.app.training

import java.net.HttpURLConnection
import java.net.URL

data class TrainingRuntimeConfig(
    val runtimeMode: String,
    val activeArtifact: String,
    val liveDataEnabled: Boolean,
    val offlineFallbackEnabled: Boolean,
    val recoveryEnabled: Boolean,
)

data class ActiveTrainingArtifact(
    val artifactId: String,
    val version: String,
    val stage: String,
    val qualityScore: Double,
    val regressionCount: Int,
)

class TrainingRuntimeConfigClient(
    private val apiBaseUrl: String,
    private val connectTimeoutMs: Int = 3_000,
    private val readTimeoutMs: Int = 5_000,
) {
    fun fetchRuntimeConfig(): TrainingRuntimeConfig? =
        getJson("/api/training/runtime-config")?.let { body ->
            TrainingRuntimeConfig(
                runtimeMode = value(body, "runtime_mode") ?: return@let null,
                activeArtifact = value(body, "active_artifact") ?: return@let null,
                liveDataEnabled = value(body, "live_data_enabled")?.toBoolean() ?: false,
                offlineFallbackEnabled = value(body, "offline_fallback_enabled")?.toBoolean() ?: false,
                recoveryEnabled = value(body, "recovery_enabled")?.toBoolean() ?: false,
            )
        }

    fun fetchActiveArtifact(): ActiveTrainingArtifact? =
        getJson("/api/training/artifacts/active")?.let { body ->
            ActiveTrainingArtifact(
                artifactId = value(body, "artifact_id") ?: return@let null,
                version = value(body, "version") ?: return@let null,
                stage = value(body, "stage") ?: return@let null,
                qualityScore = value(body, "quality_score")?.toDoubleOrNull() ?: return@let null,
                regressionCount = value(body, "regression_count")?.toIntOrNull() ?: return@let null,
            )
        }

    private fun getJson(path: String): String? = runCatching {
        val connection = (URL(apiBaseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun value(body: String, key: String): String? =
        Regex("\"" + key + "\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}]+))")
            .find(body)
            ?.let { match -> match.groupValues[1].ifEmpty { match.groupValues[2] }.trim() }
}
