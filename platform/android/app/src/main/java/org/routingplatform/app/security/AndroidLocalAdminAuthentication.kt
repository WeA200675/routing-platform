package org.routingplatform.app.security

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent

/**
 * Platform device-credential boundary for the local post-drive security surface.
 * It never grants access when the device has no secure lock screen configured.
 */
class AndroidLocalAdminAuthentication(
    context: Context,
    private val access: DrivingSecurityAdminAccess,
) {
    private val keyguard = context.applicationContext.getSystemService(KeyguardManager::class.java)

    fun confirmationIntent(): Intent? {
        if (!keyguard.isDeviceSecure) return null
        @Suppress("DEPRECATION")
        return keyguard.createConfirmDeviceCredentialIntent(
            "Security-Diagnose",
            "Geräteauthentifizierung für lokale Navigations-Sicherheitsdaten",
        )
    }

    fun authorizationFromResult(
        resultCode: Int,
        nowElapsedRealtimeNanos: Long,
    ): DrivingSecurityAdminAccess.Authorization? {
        if (resultCode != Activity.RESULT_OK || !keyguard.isDeviceSecure) return null
        return access.authorizeAfterSuccessfulDeviceAuthentication(nowElapsedRealtimeNanos)
    }
}
