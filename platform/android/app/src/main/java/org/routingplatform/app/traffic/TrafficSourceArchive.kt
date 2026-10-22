package org.routingplatform.app.traffic

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Local, auditable archive for traffic/source observations.
 *
 * It stores metadata and validation outcomes, never personal identifiers or
 * raw Wi-Fi/Bluetooth/GNSS observations. Archived values do not mutate routing.
 */
internal data class TrafficSourceRecord(
    val sourceId: String,
    val provider: String,
    val dataType: String,
    val endpoint: String?,
    val retrievedAtEpochMs: Long,
    val coverage: String,
    val travelDirection: String?,
    val freshnessSeconds: Long?,
    val validation: String,
    val rejectionReason: String?,
)

internal object TrafficSourceArchive {
    private const val PREFS = "traffic_source_archive_v1"
    private const val KEY_RECORDS = "records"
    private const val MAX_RECORDS = 2000

    fun records(context: Context): List<TrafficSourceRecord> {
        val rows = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECORDS, "[]"))
        }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { i -> runCatching {
            val o = rows.getJSONObject(i)
            TrafficSourceRecord(
                sourceId = o.getString("sourceId"),
                provider = o.getString("provider"),
                dataType = o.getString("dataType"),
                endpoint = o.optString("endpoint").ifBlank { null },
                retrievedAtEpochMs = o.getLong("retrievedAtEpochMs"),
                coverage = o.optString("coverage", "unknown"),
                travelDirection = o.optString("travelDirection").ifBlank { null },
                freshnessSeconds = if (o.has("freshnessSeconds")) o.optLong("freshnessSeconds") else null,
                validation = o.optString("validation", "unknown"),
                rejectionReason = o.optString("rejectionReason").ifBlank { null },
            )
        }.getOrNull() }
    }

    @Synchronized
    fun append(context: Context, record: TrafficSourceRecord) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val rows = JSONArray()
        (records(context) + record).takeLast(MAX_RECORDS).forEach { item ->
            rows.put(JSONObject().apply {
                put("sourceId", item.sourceId)
                put("provider", item.provider)
                put("dataType", item.dataType)
                put("endpoint", item.endpoint ?: "")
                put("retrievedAtEpochMs", item.retrievedAtEpochMs)
                put("coverage", item.coverage)
                put("travelDirection", item.travelDirection ?: "")
                item.freshnessSeconds?.let { put("freshnessSeconds", it) }
                put("validation", item.validation)
                put("rejectionReason", item.rejectionReason ?: "")
            })
        }
        preferences.edit().putString(KEY_RECORDS, rows.toString()).apply()
    }
}

/**
 * Bounded refresh worker. Feed responses are treated as evidence only; this
 * worker does not reroute, alter progress, or speak a maneuver.
 */
internal class TrafficSourceRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : Worker(context, parameters) {
    override fun doWork(): Result {
        val now = System.currentTimeMillis()
        val endpoints = inputData.getStringArray(KEY_ENDPOINTS).orEmpty()
        if (endpoints.isEmpty()) {
            TrafficSourceArchive.append(applicationContext, unavailable(now, "no_configured_source"))
            return Result.success()
        }

        var succeeded = false
        endpoints.take(MAX_ENDPOINTS).forEachIndexed { index, endpoint ->
            val result = fetchMetadata(endpoint)
            TrafficSourceArchive.append(
                applicationContext,
                result.copy(sourceId = "refresh-$now-$index"),
            )
            succeeded = succeeded || result.validation == "validated"
        }
        return if (succeeded) Result.success() else Result.retry()
    }

    private fun fetchMetadata(endpoint: String): TrafficSourceRecord {
        val now = System.currentTimeMillis()
        val safeEndpoint = endpoint.takeIf { it.startsWith("https://") }?.take(MAX_ENDPOINT_LENGTH)
        if (safeEndpoint == null) return unavailable(now, "endpoint_not_allowed")
        return runCatching {
            val connection = URL(safeEndpoint).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 2500
                connection.readTimeout = 3500
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "application/json, application/xml, text/xml")
                val status = connection.responseCode
                if (status !in 200..299) return@runCatching unavailable(now, "http_$status").copy(endpoint = safeEndpoint)
                val bodyBytes = connection.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES) }
                if (bodyBytes.isEmpty()) return@runCatching unavailable(now, "empty_response").copy(endpoint = safeEndpoint)
                TrafficSourceRecord(
                    sourceId = "source-$now",
                    provider = safeEndpoint.substringAfter("://").substringBefore('/'),
                    dataType = "traffic_or_hazard_feed",
                    endpoint = safeEndpoint,
                    retrievedAtEpochMs = now,
                    coverage = "unverified",
                    travelDirection = null,
                    freshnessSeconds = null,
                    validation = "metadata_received",
                    rejectionReason = "route_match_required_before_display",
                )
            } finally {
                connection.disconnect()
            }
        }.getOrElse { unavailable(now, "network_or_parse_error").copy(endpoint = safeEndpoint) }
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(8192)
        val output = java.io.ByteArrayOutputStream(minOf(limit, buffer.size))
        var total = 0
        while (total < limit) {
            val read = input.read(buffer, 0, minOf(buffer.size, limit - total))
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun unavailable(now: Long, reason: String) = TrafficSourceRecord(
        sourceId = "unavailable-$now",
        provider = "none",
        dataType = "traffic_or_hazard_feed",
        endpoint = null,
        retrievedAtEpochMs = now,
        coverage = "unknown",
        travelDirection = null,
        freshnessSeconds = null,
        validation = "unavailable",
        rejectionReason = reason,
    )

    companion object {
        private const val KEY_ENDPOINTS = "source_endpoints"
        private const val MAX_ENDPOINTS = 4
        private const val MAX_ENDPOINT_LENGTH = 512
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val USER_AGENT = "RoutingPlatform/1.0 (+https://github.com/WeA200675/routing-platform)"
        private const val WORK_NAME = "traffic_source_refresh"

        fun schedule(context: Context, endpoints: List<String>) {
            val request = OneTimeWorkRequestBuilder<TrafficSourceRefreshWorker>()
                .setInputData(androidx.work.Data.Builder().putStringArray(KEY_ENDPOINTS, endpoints.take(MAX_ENDPOINTS).toTypedArray()).build())
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setInitialDelay(5, TimeUnit.MINUTES)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
