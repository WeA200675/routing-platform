package org.routingplatform.app.intelligence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun acceptsEmptyResponseWithoutSignature() {
        assertTrue(
            Ed25519NavigationEventVerifier.verify(
                events = emptyList(),
                trustedPublicKeys = emptyMap(),
            ),
        )
    }
}
