package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRoutingAdmissionTest {
    private val valid = OfflineRoutingDataset(
        datasetId = "region-de-by",
        version = "2026-09-22",
        capturedAtElapsedRealtimeNanos = 100L,
        integrityVerified = true,
    )

    @Test fun admitsVerifiedFreshDataset() {
        assertEquals(
            OfflineRoutingAvailability.Available,
            OfflineRoutingAdmission.availability(valid, 200L, 100L),
        )
        assertTrue(OfflineRoutingAdmission.mayRouteOffline(valid, 200L, 100L))
    }

    @Test fun rejectsMissingAndInvalidIdentity() {
        assertEquals(
            OfflineRoutingAvailability.Missing,
            OfflineRoutingAdmission.availability(null, 200L, 100L),
        )
        assertEquals(
            OfflineRoutingAvailability.InvalidIdentity,
            OfflineRoutingAdmission.availability(valid.copy(datasetId = " "), 200L, 100L),
        )
        assertEquals(
            OfflineRoutingAvailability.InvalidIdentity,
            OfflineRoutingAdmission.availability(valid.copy(version = ""), 200L, 100L),
        )
    }

    @Test fun rejectsUnverifiedFutureAndStaleDataset() {
        assertEquals(
            OfflineRoutingAvailability.IntegrityFailure,
            OfflineRoutingAdmission.availability(valid.copy(integrityVerified = false), 200L, 100L),
        )
        assertEquals(
            OfflineRoutingAvailability.FutureDated,
            OfflineRoutingAdmission.availability(valid.copy(capturedAtElapsedRealtimeNanos = 201L), 200L, 100L),
        )
        assertEquals(
            OfflineRoutingAvailability.Stale,
            OfflineRoutingAdmission.availability(valid, 201L, 100L),
        )
    }

    @Test fun invalidClockOrBudgetFailsClosedWithPreciseReason() {
        assertEquals(
            OfflineRoutingAvailability.InvalidClock,
            OfflineRoutingAdmission.availability(valid, -1L, 100L),
        )
        assertEquals(
            OfflineRoutingAvailability.InvalidFreshnessBudget,
            OfflineRoutingAdmission.availability(valid, 200L, -1L),
        )
        assertEquals(
            OfflineRoutingAvailability.InvalidTimestamp,
            OfflineRoutingAdmission.availability(
                valid.copy(capturedAtElapsedRealtimeNanos = -1L),
                200L,
                100L,
            ),
        )
        assertFalse(OfflineRoutingAdmission.mayRouteOffline(valid, -1L, 100L))
        assertFalse(OfflineRoutingAdmission.mayRouteOffline(valid, 200L, -1L))
    }

    @Test fun recoveryPublishesIdentityOnlyForAdmittedDataset() {
        val recovered = OfflineRoutingRecovery.recover(valid, 200L, 100L)
        assertTrue(recovered.routingAvailable)
        assertEquals("region-de-by", recovered.activeDatasetId)
        assertEquals("2026-09-22", recovered.activeVersion)

        val rejected = OfflineRoutingRecovery.recover(
            valid.copy(integrityVerified = false),
            200L,
            100L,
        )
        assertFalse(rejected.routingAvailable)
        assertEquals(null, rejected.activeDatasetId)
        assertEquals(null, rejected.activeVersion)
        assertEquals(OfflineRoutingAvailability.IntegrityFailure, rejected.availability)
    }

    @Test fun recoveryDoesNotRetainStaleDatasetIdentity() {
        val rejected = OfflineRoutingRecovery.recover(valid, 201L, 100L)
        assertFalse(rejected.routingAvailable)
        assertEquals(null, rejected.activeDatasetId)
        assertEquals(null, rejected.activeVersion)
        assertEquals(OfflineRoutingAvailability.Stale, rejected.availability)
    }
}
