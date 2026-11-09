package org.routingplatform.app.training

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class AndroidFifoJournalPersistence(
    context: Context,
) : FifoJournalPersistence {
    private val preferences = context.applicationContext.getSharedPreferences(
        "training-fifo-journal",
        Context.MODE_PRIVATE,
    )

    override fun load(): PersistedJournal? {
        val raw = preferences.getString(KEY_PAYLOAD, null) ?: return null
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val eventsJson = root.optJSONArray("events") ?: return null
        val events = buildList {
            for (index in 0 until eventsJson.length()) {
                val item = eventsJson.optJSONObject(index) ?: continue
                add(
                    TrainingEvent(
                        eventId = item.getString("event_id"),
                        schemaVersion = item.getString("schema_version"),
                        eventType = item.getString("event_type"),
                        sessionId = item.optString("session_id").ifBlank { null },
                        routeId = item.optString("route_id").ifBlank { null },
                        capturedAtEpochMs = item.getLong("captured_at_epoch_ms"),
                        source = item.getString("source"),
                        qualityScore = item.getDouble("quality_score"),
                        payload = item.getString("payload"),
                        sizeBytes = item.getInt("size_bytes"),
                        protected = item.optBoolean("protected", false),
                    ),
                )
            }
        }
        val integrity = JournalIntegrity(
            eventCount = root.optInt("event_count", -1),
            totalBytes = root.optLong("total_bytes", -1L),
            digest = root.optString("digest"),
        )
        return PersistedJournal(events, integrity)
    }

    override fun save(events: List<TrainingEvent>, integrity: JournalIntegrity) {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("event_id", event.eventId)
                    .put("schema_version", event.schemaVersion)
                    .put("event_type", event.eventType)
                    .put("session_id", event.sessionId)
                    .put("route_id", event.routeId)
                    .put("captured_at_epoch_ms", event.capturedAtEpochMs)
                    .put("source", event.source)
                    .put("quality_score", event.qualityScore)
                    .put("payload", event.payload)
                    .put("size_bytes", event.sizeBytes)
                    .put("protected", event.protected),
            )
        }
        val root = JSONObject()
            .put("event_count", integrity.eventCount)
            .put("total_bytes", integrity.totalBytes)
            .put("digest", integrity.digest)
            .put("events", array)
        preferences.edit().putString(KEY_PAYLOAD, root.toString()).apply()
    }

    private companion object {
        const val KEY_PAYLOAD = "journal"
    }
}
