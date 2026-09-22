package org.routingplatform.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingInterferenceEvidenceTest {
    @Test
    fun sameEvidenceProducesSameDigestWithoutRetainingPayload() {
        val first = record("probe".toByteArray())
        val second = record("probe".toByteArray())
        assertEquals(first.eventId, second.eventId)
        assertEquals(64, first.eventId.length)
    }

    @Test
    fun payloadOrKindChangesDigest() {
        val first = record("probe".toByteArray())
        val second = record("other".toByteArray())
        val integrity = record("probe".toByteArray(), DrivingInterferenceKind.IntegrityFailure)
        assert(first.eventId != second.eventId)
        assert(first.eventId != integrity.eventId)
    }

    @Test
    fun positionIsOptionalWhenFreshTrustedObservationIsUnavailable() {
        val value = record("probe".toByteArray())
        assertNull(value.vehiclePosition)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidEvidencePosition() {
        EvidenceVehiclePosition(91.0, 11.0, 5.0, 10L)
    }

    private fun record(
        payload: ByteArray,
        kind: DrivingInterferenceKind = DrivingInterferenceKind.ConnectivityChange,
    ) = DrivingInterferenceEvidence.create(
        kind = kind,
        utcEpochMillis = 1_700_000_000_000L,
        elapsedRealtimeNanos = 10L,
        sessionReference = "session-hash",
        source = "android-connectivity",
        action = "record-only",
        integrityState = "unchanged",
        payload = payload,
    )
}
