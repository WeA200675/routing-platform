package org.routingplatform.app.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Small on-device store for route metrics. It intentionally stores no destination or coordinates. */
class AndroidDriveStatisticsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "routing_platform_drive_statistics_v1",
        Context.MODE_PRIVATE,
    )

    @Synchronized
    fun load(): List<CompletedDrive> = runCatching {
        val encoded = preferences.getString(KEY_DRIVES, null) ?: return emptyList()
        val array = JSONArray(encoded)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    CompletedDrive(
                        completedAtEpochMillis = item.getLong("at"),
                        distanceMeters = item.getInt("distance"),
                        durationSeconds = item.getInt("duration"),
                        routeFamily = item.getString("family"),
                    )
                )
            }
        }.takeLast(MAX_RECORDS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun record(drive: CompletedDrive): List<CompletedDrive> {
        val updated = (load() + drive).takeLast(MAX_RECORDS)
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject()
                    .put("at", item.completedAtEpochMillis)
                    .put("distance", item.distanceMeters)
                    .put("duration", item.durationSeconds)
                    .put("family", item.routeFamily)
            )
        }
        preferences.edit().putString(KEY_DRIVES, array.toString()).apply()
        return updated
    }

    @Synchronized
    fun loadHistory(routeKey: String): List<HistoricalRouteObservation> = runCatching {
        requireValidRouteKey(routeKey)
        val encoded = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        val array = JSONArray(encoded)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                if (item.getString("route") == routeKey) {
                    add(
                        HistoricalRouteObservation(
                            routeKey = routeKey,
                            startedAtEpochMillis = item.getLong("at"),
                            baselineDurationSeconds = item.getInt("baseline"),
                            observedDurationSeconds = item.getInt("observed"),
                        )
                    )
                }
            }
        }.takeLast(MAX_RECORDS)
    }.getOrDefault(emptyList())

    @Synchronized
    fun recordHistory(observation: HistoricalRouteObservation) {
        val array = runCatching {
            JSONArray(preferences.getString(KEY_HISTORY, "[]"))
        }.getOrDefault(JSONArray())
        array.put(
            JSONObject()
                .put("route", observation.routeKey)
                .put("at", observation.startedAtEpochMillis)
                .put("baseline", observation.baselineDurationSeconds)
                .put("observed", observation.observedDurationSeconds)
        )
        val bounded = JSONArray()
        val start = (array.length() - MAX_RECORDS).coerceAtLeast(0)
        for (index in start until array.length()) bounded.put(array.get(index))
        preferences.edit().putString(KEY_HISTORY, bounded.toString()).apply()
    }

    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_DRIVES).remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_DRIVES = "completed_drives"
        private const val KEY_HISTORY = "route_history"
        private const val MAX_RECORDS = 500

        /** Stable, one-way local route key. Raw geometry is never persisted. */
        fun routeKeyForGeometry(points: List<RoutePoint>): String {
            require(points.size >= 2)
            val canonical = points.joinToString("|") {
                "${"%.5f".format(Locale.ROOT, it.latitude)},${"%.5f".format(Locale.ROOT, it.longitude)}"
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
            return "route-" + digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        private fun requireValidRouteKey(value: String) {
            require(value.matches(Regex("route-[a-f0-9]{32}")))
        }
    }
}
