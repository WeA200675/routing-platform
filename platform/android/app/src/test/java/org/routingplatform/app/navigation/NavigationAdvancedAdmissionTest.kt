package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAdvancedAdmissionTest {
    @Test fun provenanceRequiresCanonicalSha256AndSource() {
        assertTrue(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "a".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(null))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance(" ", "a".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "A".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "a".repeat(63))
        ))
    }

    @Test fun restoreRequiresVersionIdentityAndFreshMonotonicState() {
        val valid = PersistedNavigationState(1, "session-a", 100L)
        assertTrue(NavigationStateRestoreAdmission.mayRestore(valid, 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(schemaVersion = 2), 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(sessionId = ""), 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(savedAtElapsedRealtimeNanos = 201L), 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid, 201L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid, -1L, 100L))
    }
}
