package org.routingplatform.app.security

/**
 * Storage boundary for sensitive post-drive evidence.
 *
 * Implementations must encrypt records at rest with platform-backed keys,
 * keep them local by default, and enforce retention before returning records.
 * The navigation/security observer depends only on this contract so a future
 * Apple adapter can provide Keychain/Secure-Enclave-backed storage without
 * changing evidence semantics.
 */
interface DrivingInterferenceEvidenceStore {
    fun append(record: DrivingInterferenceEvidenceRecord)
    fun readForAuthenticatedAdmin(nowUtcEpochMillis: Long): List<DrivingInterferenceEvidenceRecord>
    fun purgeExpired(nowUtcEpochMillis: Long)
}

object DrivingInterferenceRetention {
    const val MAX_AGE_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    fun isRetained(record: DrivingInterferenceEvidenceRecord, nowUtcEpochMillis: Long): Boolean {
        if (nowUtcEpochMillis < record.utcEpochMillis) return false
        return nowUtcEpochMillis - record.utcEpochMillis <= MAX_AGE_MILLIS
    }

    fun retained(
        records: List<DrivingInterferenceEvidenceRecord>,
        nowUtcEpochMillis: Long,
    ): List<DrivingInterferenceEvidenceRecord> =
        records.filter { isRetained(it, nowUtcEpochMillis) }
            .sortedWith(compareBy({ it.utcEpochMillis }, { it.eventId }))
}
