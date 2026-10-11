package org.routingplatform.app.ui

import org.routingplatform.app.navigation.NavigationDeviceCalibrationProfile
import java.util.Locale

internal object NavigationCalibrationPresentation {
    fun summary(profile: NavigationDeviceCalibrationProfile): String {
        val accuracy = profile.bestObservedAccuracyM?.let { String.format(Locale.ROOT, "%.1f m", it) } ?: "keine"
        return "${profile.acceptedDirectSamples} direkt akzeptiert · ${profile.rejectedSamples} verworfen · beste Genauigkeit $accuracy"
    }
}