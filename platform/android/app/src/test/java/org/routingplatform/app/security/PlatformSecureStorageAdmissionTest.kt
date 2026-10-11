package org.routingplatform.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformSecureStorageAdmissionTest {
    @Test fun osBackedAuthenticatedStorageIsAdmitted() {
        assertTrue(SecureStorageAdmission(
            PlatformSecureStorageCapability.OsBacked,
            authenticatedAccess = true,
        ).mayStoreSensitiveEvidence)
    }

    @Test fun unavailableOrUnauthenticatedStorageFailsClosed() {
        assertFalse(SecureStorageAdmission(
            PlatformSecureStorageCapability.Unavailable,
            authenticatedAccess = true,
        ).mayStoreSensitiveEvidence)
        assertFalse(SecureStorageAdmission(
            PlatformSecureStorageCapability.HardwareBacked,
            authenticatedAccess = false,
        ).mayStoreSensitiveEvidence)
    }
}
