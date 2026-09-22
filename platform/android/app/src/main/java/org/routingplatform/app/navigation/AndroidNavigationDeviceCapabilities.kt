package org.routingplatform.app.navigation

import android.content.Context

/** Manufacturer-neutral capability detection for calibration admission. */
object AndroidNavigationDeviceCapabilities {
    fun detect(context: Context): NavigationDeviceCapabilities =
        NavigationDeviceCapabilities(
            preciseLocationAvailable = hasPreciseNavigationLocationPermission(context),
            directObservationAvailable = true,
            monotonicTimestampAvailable = true,
        )

    fun calibrationAvailable(capabilities: NavigationDeviceCapabilities): Boolean =
        capabilities.preciseLocationAvailable &&
            capabilities.directObservationAvailable &&
            capabilities.monotonicTimestampAvailable
}
