package org.routingplatform.app.navigation

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * Transactional FIFO. Rows are removed only after a durable server acknowledgement.
 * Legacy SharedPreferences snapshots are migrated once, without deleting the source
 * until all rows have been committed.
 */
class NavigationDiagnosticsUploadQueue(context: Context) {
    private val appContext = context.applicationContext
    private val dbHelper = object : SQLiteOpenHelper(appContext, "diagnostics-outbox.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("Unsupported diagnostics database migration")
        }
    }

    init { migrateLegacy() }

    @Synchronized
    fun enqueue(snapshot: JSONObject) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            check(count(db) < MAX_PACKAGES) { "Diagnostics outbox full; original events retained" }
            val values = ContentValues().apply { put("body", snapshot.toString()) }
            check(db.insertOrThrow("snapshots", null, values) > 0)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun peek(): JSONObject? {
        val db = dbHelper.readableDatabase
        db.rawQuery("SELECT body FROM snapshots ORDER BY id LIMIT 1", null).use { cursor ->
            return if (cursor.moveToFirst()) JSONObject(cursor.getString(0)) else null
        }
    }

    /** Delete only the exact snapshot uploaded; never delete a concurrently replaced head. */
    @Synchronized
    fun acknowledge(snapshot: JSONObject): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val first = db.rawQuery("SELECT id, body FROM snapshots ORDER BY id LIMIT 1", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) to c.getString(1) else null
            } ?: return false
            if (first.second != snapshot.toString()) return false
            val deleted = db.delete("snapshots", "id=?", arrayOf(first.first.toString())) == 1
            if (deleted) db.setTransactionSuccessful()
            return deleted
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun acknowledgeFirst() {
        val snapshot = peek() ?: return
        check(acknowledge(snapshot))
    }

    @Synchronized
    fun size(): Int = count(dbHelper.readableDatabase)

    @Synchronized
    fun clear() {
        dbHelper.writableDatabase.delete("snapshots", null, null)
    }

    private fun count(db: SQLiteDatabase): Int =
        db.rawQuery("SELECT COUNT(*) FROM snapshots", null).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    @Synchronized
    private fun migrateLegacy() {
        val prefs = appContext.getSharedPreferences("navigation-diagnostics-upload", Context.MODE_PRIVATE)
        val source = prefs.getString("pending_packages", null) ?: return
        val old = JSONArray(source)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // A migration is only safe if it cannot duplicate or exceed the queue.
            check(count(db) == 0) { "Legacy migration interrupted: manual recovery required" }
            check(old.length() <= MAX_PACKAGES)
            for (index in 0 until old.length()) {
                val item = old.getJSONObject(index)
                db.insertOrThrow("snapshots", null, ContentValues().apply { put("body", item.toString()) })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // Keep a recoverable legacy copy until migration is committed.
        check(prefs.edit().remove("pending_packages").commit())
    }

    private companion object {
        const val MAX_PACKAGES = 10_000
    }
}
