package org.routingplatform.app.security

/**
 * Store reads are reachable through an explicit short-lived authorization gate.
 * The UI must mint a token only after successful local device authentication.
 */
class DrivingSecurityAdminAccess(private val store: DrivingInterferenceEvidenceStore) {
    class Authorization internal constructor(internal val expiresElapsedRealtimeNanos: Long)

    fun authorizeAfterSuccessfulDeviceAuthentication(
        nowElapsedRealtimeNanos: Long,
    ): Authorization {
        require(nowElapsedRealtimeNanos >= 0L)
        return Authorization(nowElapsedRealtimeNanos + AUTH_WINDOW_NANOS)
    }

    fun read(
        authorization: Authorization,
        nowElapsedRealtimeNanos: Long,
        nowUtcEpochMillis: Long,
    ): List<DrivingInterferenceEvidenceRecord> {
        require(nowElapsedRealtimeNanos >= 0L)
        if (nowElapsedRealtimeNanos > authorization.expiresElapsedRealtimeNanos) {
            throw SecurityException("Local admin authorization expired")
        }
        return store.readForAuthenticatedAdmin(nowUtcEpochMillis)
    }

    private companion object { const val AUTH_WINDOW_NANOS = 60_000_000_000L }
}
