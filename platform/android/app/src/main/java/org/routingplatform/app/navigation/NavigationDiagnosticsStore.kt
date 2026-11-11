package org.routingplatform.app.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class NavigationDiagnosticsSummary(
    val sampleCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val successRate: Double,
    val averageLatencyMs: Long,
    val lastCapturedAtEpochMs: Long?,
)

data class NavigationProviderDiagnostic(
    val providerId: String,
    val capturedAtEpochMs: Long,
    val success: Boolean,
    val eventCount: Int,
    val acceptedEventCount: Int,
    val latencyMs: Long,
    val fallbackUsed: Boolean,
    val error: String?,
)

class NavigationDiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences("navigation-diagnostics", Context.MODE_PRIVATE)

    @Synchronized
    fun recordUserError(
        trigger: String,
        routeId: String?,
        latitude: Double?,
        longitude: Double?,
    ) {
        val array = read()
        array.put(JSONObject().apply {
            put("provider_id", "user-error")
            put("captured_at", System.currentTimeMillis())
            put("success", false)
            put("event_count", 1)
            put("accepted_event_count", 0)
            put("latency_ms", 0)
            put("fallback_used", false)
            put("error", trigger)
            put("route_id", routeId)
            put("latitude", latitude)
            put("longitude", longitude)
        })
        while (array.length() > 500) array.remove(0)
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    @Synchronized
    fun record(entry: NavigationProviderDiagnostic) {
        val array = read()
        array.put(JSONObject().apply {
            put("provider_id", entry.providerId)
            put("captured_at", entry.capturedAtEpochMs)
            put("success", entry.success)
            put("event_count", entry.eventCount)
            put("accepted_event_count", entry.acceptedEventCount)
            put("latency_ms", entry.latencyMs)
            put("fallback_used", entry.fallbackUsed)
            put("error", entry.error)
        })
        while (array.length() > 500) array.remove(0)
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    fun readAll(): List<NavigationProviderDiagnostic> {
        val array = read()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(NavigationProviderDiagnostic(
                    providerId = item.optString("provider_id"),
                    capturedAtEpochMs = item.optLong("captured_at"),
                    success = item.optBoolean("success"),
                    eventCount = item.optInt("event_count"),
                    acceptedEventCount = item.optInt("accepted_event_count"),
                    latencyMs = item.optLong("latency_ms"),
                    fallbackUsed = item.optBoolean("fallback_used"),
                    error = item.optString("error").takeIf { it.isNotEmpty() },
                ))
            }
        }
    }

    fun summary(): NavigationDiagnosticsSummary {
        val entries = readAll()
        val successes = entries.count { it.success }
        return NavigationDiagnosticsSummary(
            sampleCount = entries.size,
            successCount = successes,
            failureCount = entries.size - successes,
            successRate = if (entries.isEmpty()) 0.0 else successes.toDouble() / entries.size,
            averageLatencyMs = if (entries.isEmpty()) 0L else entries.map { it.latencyMs }.average().toLong(),
            lastCapturedAtEpochMs = entries.maxOfOrNull { it.capturedAtEpochMs },
        )
    }

    fun snapshotJson(): JSONObject {
        val entries = readAll()
        val events = JSONArray()
        entries.forEachIndexed { index, entry ->
            events.put(
                JSONObject().apply {
                    put("event_id", "provider-diagnostic-${entry.capturedAtEpochMs}-$index")
                    put("type", "navigation-provider-diagnostic")
                    put("captured_at", entry.capturedAtEpochMs)
                    put("size_bytes", 0)
                    put("payload", JSONObject().apply {
                        put("provider_id", entry.providerId)
                        put("success", entry.success)
                        put("event_count", entry.eventCount)
                        put("accepted_event_count", entry.acceptedEventCount)
                        put("latency_ms", entry.latencyMs)
                        put("fallback_used", entry.fallbackUsed)
                        put("error", entry.error)
                    })
                },
            )
        }
        return JSONObject().apply {
            put("schema_version", "navigation-diagnostics-v1")
            put("captured_at", System.currentTimeMillis())
            put("summary", JSONObject().apply {
                val summary = summary()
                put("sample_count", summary.sampleCount)
                put("success_count", summary.successCount)
                put("failure_count", summary.failureCount)
                put("success_rate", summary.successRate)
                put("average_latency_ms", summary.averageLatencyMs)
                put("last_captured_at", summary.lastCapturedAtEpochMs)
            })
            put("events", events)
        }
    }

    fun snapshotAndEnqueue(queue: NavigationDiagnosticsUploadQueue): JSONObject {
        val snapshot = snapshotJson()
        queue.enqueue(snapshot)
        return snapshot
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun read(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private companion object { const val KEY = "provider_diagnostics" }
}
