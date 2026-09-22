package org.routingplatform.app.navigation

import android.content.Context

/** Explicit first-use disclosure state; calibration does not run before acceptance. */
class AndroidNavigationCalibrationDisclosureStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun accepted(): Boolean = prefs.getBoolean(KEY_ACCEPTED, false)
    fun accept() { prefs.edit().putBoolean(KEY_ACCEPTED, true).apply() }
    fun reset() { prefs.edit().clear().apply() }

    private companion object {
        const val PREFS = "navigation-calibration-disclosure-v1"
        const val KEY_ACCEPTED = "accepted"
    }
}
