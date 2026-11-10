package org.routingplatform.app.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

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

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun read(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private companion object { const val KEY = "provider_diagnostics" }
}
