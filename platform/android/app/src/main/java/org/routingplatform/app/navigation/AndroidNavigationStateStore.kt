package org.routingplatform.app.navigation

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings

/**
 * Small fail-closed persistence boundary for resumable navigation identity.
 * Monotonic timestamps are only meaningful within the same boot, therefore
 * every record is bound to a caller-supplied boot identity.
 */
class AndroidNavigationStateStore internal constructor(
    private val storage: NavigationStateStorage,
) {
    constructor(context: Context) : this(
        SharedPreferencesNavigationStateStorage(
            context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        )
    )

    fun save(state: PersistedNavigationState): Boolean {
        if (state.schemaVersion != NavigationStateRestoreAdmission.SCHEMA_VERSION ||
            !validIdentity(state.sessionId) || !validIdentity(state.bootId) ||
            state.savedAtElapsedRealtimeNanos < 0L) return false
        val encoded = listOf(
            state.schemaVersion.toString(),
            state.sessionId,
            state.bootId,
            state.savedAtElapsedRealtimeNanos.toString(),
        ).joinToString("|")
        if (encoded.length > MAX_ENCODED_CHARS || encoded.contains('\n') || encoded.contains('\r')) return false
        return storage.write(encoded)
    }

    fun restore(
        currentBootId: String,
        nowElapsedRealtimeNanos: Long,
        maximumAgeNanos: Long,
    ): PersistedNavigationState? {
        val raw = storage.read() ?: return null
        if (raw.length > MAX_ENCODED_CHARS) return clearAndNull()
        val parts = raw.split('|')
        if (parts.size != 4) return clearAndNull()
        val state = PersistedNavigationState(
            schemaVersion = parts[0].toIntOrNull() ?: return clearAndNull(),
            sessionId = parts[1],
            bootId = parts[2],
            savedAtElapsedRealtimeNanos = parts[3].toLongOrNull() ?: return clearAndNull(),
        )
        return if (
            NavigationStateRestoreAdmission.mayRestore(
                state,
                currentBootId,
                nowElapsedRealtimeNanos,
                maximumAgeNanos,
            )
        ) state else clearAndNull()
    }

    fun clear(): Boolean = storage.clear()

    private fun validIdentity(value: String): Boolean =
        value.isNotBlank() && value.length <= MAX_IDENTITY_CHARS &&
            !value.contains('|') && !value.contains('\n') && !value.contains('\r')

    private fun clearAndNull(): PersistedNavigationState? {
        clear()
        return null
    }

    private companion object {
        const val PREFERENCES = "navigation_resume_v1"
        const val MAX_ENCODED_CHARS = 1024
        const val MAX_IDENTITY_CHARS = 256
    }
}

internal interface NavigationStateStorage {
    fun read(): String?
    fun write(value: String): Boolean
    fun clear(): Boolean
}

private class SharedPreferencesNavigationStateStorage(
    private val preferences: SharedPreferences,
) : NavigationStateStorage {
    override fun read(): String? = preferences.getString(KEY_STATE, null)
    override fun write(value: String): Boolean =
        preferences.edit().putString(KEY_STATE, value).commit()
    override fun clear(): Boolean = preferences.edit().remove(KEY_STATE).commit()

    private companion object {
        const val KEY_STATE = "state"
    }
}

/**
 * Stable-within-boot identity used to bind elapsedRealtime timestamps.
 * If Android does not expose a valid boot count, callers receive null and
 * persistence must fail closed rather than inventing a cross-boot identity.
 */
object AndroidNavigationBootIdentity {
    fun current(context: Context): String? =
        runCatching {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                Settings.Global.BOOT_COUNT,
            )
        }.getOrNull()
            ?.takeIf { it >= 0 }
            ?.let { "android-boot-$it" }
}
