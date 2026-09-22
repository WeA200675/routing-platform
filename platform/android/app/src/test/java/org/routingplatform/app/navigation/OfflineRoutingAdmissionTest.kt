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

    @Test fun invalidClockOrBudgetFailsClosed() {
        assertFalse(OfflineRoutingAdmission.mayRouteOffline(valid, -1L, 100L))
        assertFalse(OfflineRoutingAdmission.mayRouteOffline(valid, 200L, -1L))
        assertFalse(
            OfflineRoutingAdmission.mayRouteOffline(
                valid.copy(capturedAtElapsedRealtimeNanos = -1L),
                200L,
                100L,
            )
        )
    }
}
