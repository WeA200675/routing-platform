package org.routingplatform.app.intelligence

import android.util.Base64
import org.routingplatform.app.navigation.NavigationLiveEvent
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale

/**
 * Verifies detached Ed25519 signatures carried by live events.
 *
 * The provider must sign the UTF-8 canonical event payload produced here.
 * Missing or mixed key/signature material is rejected as a whole response.
 */
internal object Ed25519NavigationEventVerifier {
    fun verify(
        events: List<NavigationLiveEvent>,
        trustedPublicKeys: Map<String, String>,
    ): Boolean {
        if (events.isEmpty()) return true
        val keyId = events.first().signatureKeyId ?: return false
        val signatureText = events.first().signatureBase64 ?: return false
        if (events.any { it.signatureKeyId != keyId || it.signatureBase64 != signatureText }) return false
        val publicKeyText = trustedPublicKeys[keyId] ?: return false
        val publicKeyBytes = runCatching { Base64.decode(publicKeyText, Base64.DEFAULT) }.getOrNull()
            ?: return false
        val signatureBytes = runCatching { Base64.decode(signatureText, Base64.DEFAULT) }.getOrNull()
            ?: return false
        val key = runCatching {
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(publicKeyBytes))
        }.getOrNull() ?: return false
        return runCatching {
            Signature.getInstance("Ed25519").apply {
                initVerify(key)
                update(canonicalPayload(events).toByteArray(Charsets.UTF_8))
            }.verify(signatureBytes)
        }.getOrDefault(false)
    }

    internal fun canonicalPayload(events: List<NavigationLiveEvent>): String =
        events.sortedBy { it.id }.joinToString("\n") { event ->
            listOf(
                event.id,
                event.kind.name,
                "%.7f".format(Locale.ROOT, event.latitude),
                "%.7f".format(Locale.ROOT, event.longitude),
                event.directionDegrees?.let { "%.3f".format(Locale.ROOT, it) } ?: "",
                event.severity.toString(),
                "%.6f".format(Locale.ROOT, event.confidence),
                event.capturedAtEpochMs.toString(),
                event.validUntilEpochMs.toString(),
                event.source,
            ).joinToString("|") { value -> value.replace("\\", "\\\\").replace("|", "\\|") }
        }
}
