package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAdvancedAdmissionTest {
    @Test fun provenanceRequiresCanonicalSha256AndSource() {
        assertTrue(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "2026.09", "a".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(null))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance(" ", "2026.09", "a".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "2026.09", "A".repeat(64))
        ))
        assertFalse(OfflineRoutingProvenanceAdmission.verified(
            OfflineRoutingProvenance("provider-a", "2026.09", "a".repeat(63))
        ))
    }

    @Test fun restoreRequiresVersionIdentityAndFreshMonotonicState() {
        val valid = PersistedNavigationState(1, "session-a", "boot-a", 100L)
        assertTrue(NavigationStateRestoreAdmission.mayRestore(valid, "boot-a", 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(schemaVersion = 2), "boot-a", 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(sessionId = ""), "boot-a", 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid.copy(savedAtElapsedRealtimeNanos = 201L), "boot-a", 200L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid, "boot-a", 201L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid, "boot-a", -1L, 100L))
        assertFalse(NavigationStateRestoreAdmission.mayRestore(valid, "boot-b", 200L, 100L))
    }
}
