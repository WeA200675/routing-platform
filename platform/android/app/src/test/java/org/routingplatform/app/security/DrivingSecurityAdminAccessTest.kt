package org.routingplatform.app.security

import org.junit.Assert.assertEquals
import org.junit.Test

class DrivingSecurityAdminAccessTest {
    private class Store : DrivingInterferenceEvidenceStore {
        override fun append(record: DrivingInterferenceEvidenceRecord) = Unit
        override fun readForAuthenticatedAdmin(nowUtcEpochMillis: Long) = emptyList<DrivingInterferenceEvidenceRecord>()
        override fun purgeExpired(nowUtcEpochMillis: Long) = Unit
    }

    @Test fun freshAuthorizationAllowsLocalRead() {
        val access = DrivingSecurityAdminAccess(Store())
        val token = access.authorizeAfterSuccessfulDeviceAuthentication(10)
        assertEquals(0, access.read(token, 20, 1000).size)
    }

    @Test(expected = SecurityException::class)
    fun expiredAuthorizationFailsClosed() {
        val access = DrivingSecurityAdminAccess(Store())
        val token = access.authorizeAfterSuccessfulDeviceAuthentication(10)
        access.read(token, 60_000_000_011L, 1000)
    }
}
