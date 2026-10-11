package org.routingplatform.app.navigation

import android.content.Context

/** Local-only calibration persistence. Contains quality counters, never coordinates. */
class AndroidNavigationCalibrationStore(context: Context) : NavigationCalibrationStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(nowUtcEpochMillis: Long): NavigationDeviceCalibrationProfile {
        if (!prefs.contains(KEY_UPDATED)) return NavigationDeviceCalibrationProfile()
        val version = prefs.getInt(KEY_SCHEMA, -1)
        if (version != 1) return NavigationDeviceCalibrationProfile()
        val accepted = prefs.getInt(KEY_ACCEPTED, -1)
        val rejected = prefs.getInt(KEY_REJECTED, -1)
        val accuracyBits = prefs.getLong(KEY_ACCURACY_BITS, NO_ACCURACY)
        val accuracy = if (accuracyBits == NO_ACCURACY) null else Double.fromBits(accuracyBits)
        val updated = prefs.getLong(KEY_UPDATED, -1L)
        val profile = runCatching {
            NavigationDeviceCalibrationProfile(version, accepted, rejected, accuracy)
        }.getOrElse { return NavigationDeviceCalibrationProfile() }
        return NavigationCalibrationFreshness.usable(PersistedNavigationCalibration(profile, updated), nowUtcEpochMillis)
    }

    override fun save(profile: NavigationDeviceCalibrationProfile, nowUtcEpochMillis: Long) {
        require(nowUtcEpochMillis >= 0L)
        prefs.edit()
            .putInt(KEY_SCHEMA, profile.schemaVersion)
            .putInt(KEY_ACCEPTED, profile.acceptedDirectSamples)
            .putInt(KEY_REJECTED, profile.rejectedSamples)
            .putLong(KEY_ACCURACY_BITS, profile.bestObservedAccuracyM?.toBits() ?: NO_ACCURACY)
            .putLong(KEY_UPDATED, nowUtcEpochMillis)
            .apply()
    }

    override fun reset() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS = "navigation-device-calibration-v1"
        const val KEY_SCHEMA = "schema"
        const val KEY_ACCEPTED = "accepted"
        const val KEY_REJECTED = "rejected"
        const val KEY_ACCURACY_BITS = "accuracy_bits"
        const val KEY_UPDATED = "updated_utc_ms"
        const val NO_ACCURACY = Long.MIN_VALUE
    }
}
