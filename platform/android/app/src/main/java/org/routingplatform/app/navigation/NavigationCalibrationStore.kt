package org.routingplatform.app.navigation

data class PersistedNavigationCalibration(
    val profile: NavigationDeviceCalibrationProfile,
    val updatedUtcEpochMillis: Long,
)

interface NavigationCalibrationStore {
    fun load(nowUtcEpochMillis: Long): NavigationDeviceCalibrationProfile
    fun save(profile: NavigationDeviceCalibrationProfile, nowUtcEpochMillis: Long)
    fun reset()
}

object NavigationCalibrationFreshness {
    const val MAX_AGE_MILLIS = 90L * 24L * 60L * 60L * 1000L

    fun usable(value: PersistedNavigationCalibration?, nowUtcEpochMillis: Long): NavigationDeviceCalibrationProfile {
        if (value == null || nowUtcEpochMillis < 0L || value.updatedUtcEpochMillis < 0L) return NavigationDeviceCalibrationProfile()
        if (value.updatedUtcEpochMillis > nowUtcEpochMillis) return NavigationDeviceCalibrationProfile()
        if (nowUtcEpochMillis - value.updatedUtcEpochMillis > MAX_AGE_MILLIS) return NavigationDeviceCalibrationProfile()
        return value.profile
    }
}
