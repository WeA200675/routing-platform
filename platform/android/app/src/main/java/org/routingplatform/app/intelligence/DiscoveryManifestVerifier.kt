package org.routingplatform.app.intelligence

import java.security.MessageDigest
import java.util.Base64

data class DiscoveryManifest(
    val candidate: DiscoveryCandidate,
    val publicKeyId: String,
    val signatureBase64: String,
    val canonicalPayload: String,
)

internal object DiscoveryManifestVerifier {
    /**
     * Structural verifier. Cryptographic key lookup is injected by the host;
     * unknown keys are rejected instead of silently trusted.
     */
    fun verify(
        manifest: DiscoveryManifest,
        trustedKeyIds: Set<String>,
        signatureVerifier: (payload: ByteArray, signature: ByteArray, keyId: String) -> Boolean,
    ): Boolean {
        if (manifest.publicKeyId !in trustedKeyIds) return false
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(manifest.canonicalPayload.toByteArray())
        val signature = runCatching { Base64.getDecoder().decode(manifest.signatureBase64) }
            .getOrNull() ?: return false
        return signatureVerifier(digest, signature, manifest.publicKeyId)
    }
}
