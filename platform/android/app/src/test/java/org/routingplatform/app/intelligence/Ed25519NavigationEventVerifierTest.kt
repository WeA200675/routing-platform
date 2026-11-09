package org.routingplatform.app.intelligence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Test
import org.routingplatform.app.navigation.LiveEventKind
import org.routingplatform.app.navigation.NavigationLiveEvent

class Ed25519NavigationEventVerifierTest {
    private val event = NavigationLiveEvent(
        id = "hazard-1",
        kind = LiveEventKind.HAZARD,
        latitude = 48.1234567,
        longitude = 11.456789,
        severity = 4,
        confidence = 0.95,
        capturedAtEpochMs = 1_700_000_000_000,
        validUntilEpochMs = 1_700_000_060_000,
        source = "test",
        signatureBase64 = "invalid",
        signatureKeyId = "test-key",
    )

    @Test
    fun rejectsUnknownKey() {
        assertFalse(
            Ed25519NavigationEventVerifier.verify(
                events = listOf(event),
                trustedPublicKeys = emptyMap(),
            ),
        )
    }

    @Test
    fun rejectsMalformedPublicKey() {
        assertFalse(
            Ed25519NavigationEventVerifier.verify(
                events = listOf(event),
                trustedPublicKeys = mapOf("test-key" to "not-base64"),
            ),
        )
    }

    @Test
    fun rejectsMissingSignature() {
        assertFalse(
            Ed25519NavigationEventVerifier.verify(
                events = listOf(event.copy(signatureBase64 = null)),
                trustedPublicKeys = mapOf("test-key" to "AA=="),
            ),
        )
    }

    @Test
    fun acceptsValidEd25519Signature() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val unsigned = event.copy(signatureBase64 = null, signatureKeyId = null)
        val payload = Ed25519NavigationEventVerifier.canonicalPayload(listOf(unsigned))
        val signer = Signature.getInstance("Ed25519").apply {
            initSign(pair.private)
            update(payload.toByteArray(Charsets.UTF_8))
        }
        val signed = unsigned.copy(
            signatureKeyId = "test-key",
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
        )
        assertTrue(
            Ed25519NavigationEventVerifier.verify(
                events = listOf(signed),
                trustedPublicKeys = mapOf(
                    "test-key" to Base64.getEncoder().encodeToString(pair.public.encoded),
                ),
            ),
        )
    }

    @Test
    fun rejectsTamperedPayload() {
        val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val unsigned = event.copy(signatureBase64 = null, signatureKeyId = null)
        val signer = Signature.getInstance("Ed25519").apply {
            initSign(pair.private)
            update(Ed25519NavigationEventVerifier.canonicalPayload(listOf(unsigned)).toByteArray(Charsets.UTF_8))
        }
        val signed = unsigned.copy(
            signatureKeyId = "test-key",
            signatureBase64 = Base64.getEncoder().encodeToString(signer.sign()),
            severity = unsigned.severity - 1,
        )
        assertFalse(
            Ed25519NavigationEventVerifier.verify(
                events = listOf(signed),
                trustedPublicKeys = mapOf("test-key" to Base64.getEncoder().encodeToString(pair.public.encoded)),
            ),
        )
    }

    @Test
    fun acceptsEmptyResponseWithoutSignature() {
        assertTrue(
            Ed25519NavigationEventVerifier.verify(
                events = emptyList(),
                trustedPublicKeys = emptyMap(),
            ),
        )
    }
}
