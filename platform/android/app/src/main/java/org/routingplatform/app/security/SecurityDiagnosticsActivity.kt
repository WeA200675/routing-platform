package org.routingplatform.app.security

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Non-exported, device-authenticated, local-only post-drive evidence timeline. */
class SecurityDiagnosticsActivity : ComponentActivity() {
    private lateinit var access: DrivingSecurityAdminAccess
    private lateinit var authentication: AndroidLocalAdminAuthentication
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        output = TextView(this).apply {
            setPadding(32, 48, 32, 48)
            text = "Geräteauthentifizierung erforderlich …"
        }
        setContentView(output)
        access = DrivingSecurityAdminAccess(AndroidKeystoreEvidenceStore(applicationContext))
        authentication = AndroidLocalAdminAuthentication(applicationContext, access)
        val intent = authentication.confirmationIntent()
        if (intent == null) {
            output.text = "Sicherer Gerätesperrbildschirm erforderlich."
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_AUTH)
    }

    @Deprecated("Platform credential confirmation callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_AUTH) return
        val nowElapsed = SystemClock.elapsedRealtimeNanos()
        val token = authentication.authorizationFromResult(resultCode, nowElapsed)
        if (token == null || resultCode != Activity.RESULT_OK) {
            output.text = "Zugriff nicht autorisiert."
            return
        }
        val records = runCatching {
            access.read(token, nowElapsed, System.currentTimeMillis())
        }.getOrElse {
            output.text = "Sicherheitsdaten konnten nicht verifiziert werden."
            return
        }
        output.text = if (records.isEmpty()) {
            "Keine aufbewahrten Sicherheitsereignisse."
        } else {
            records.joinToString("\n\n") { r ->
                val position = r.vehiclePosition?.let { " · Fahrzeug ${it.latitude}, ${it.longitude} ±${it.accuracyM}m" } ?: ""
                "${r.utcEpochMillis} · ${r.kind} · ${r.source} · ${r.action} · ${r.integrityState}$position\nID ${r.eventId}"
            }
        }
    }

    private companion object { const val REQUEST_AUTH = 7011 }
}
