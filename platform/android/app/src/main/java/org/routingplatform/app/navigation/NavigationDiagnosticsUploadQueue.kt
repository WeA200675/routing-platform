package org.routingplatform.app.navigation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Durable FIFO for diagnostics packages. Upload workers can drain it when the
 * discovered training endpoint is reachable; packages survive process restarts.
 */
class NavigationDiagnosticsUploadQueue(context: Context) {
    private val prefs = context.getSharedPreferences("navigation-diagnostics-upload", Context.MODE_PRIVATE)

    @Synchronized
    fun enqueue(snapshot: JSONObject) {
        val queue = read()
        require(queue.length() < MAX_PACKAGES) { "Diagnostics outbox full: refusing silent eviction" }
        queue.put(snapshot)
        check(prefs.edit().putString(KEY, queue.toString()).commit()) { "Diagnostics outbox write failed" }
    }

    @Synchronized
    fun peek(): JSONObject? = read().optJSONObject(0)

    @Synchronized
    fun acknowledgeFirst() {
        val queue = read()
        if (queue.length() > 0) queue.remove(0)
        check(prefs.edit().putString(KEY, queue.toString()).commit()) { "Diagnostics outbox ACK write failed" }
    }

    @Synchronized
    fun size(): Int = read().length()

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun read(): JSONArray =
        runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())

    private companion object {
        const val KEY = "pending_packages"
        const val MAX_PACKAGES = 64
    }
}
