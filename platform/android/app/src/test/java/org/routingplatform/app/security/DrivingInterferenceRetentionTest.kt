package org.routingplatform.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingInterferenceRetentionTest {
    private fun record(utc: Long, suffix: String) = DrivingInterferenceEvidence.create(
        DrivingInterferenceKind.ConnectivityChange, utc, utc.coerceAtLeast(0),
        "session", "android-connectivity", "record-only", "unchanged", suffix.toByteArray()
    )

    @Test fun dropsFutureAndExpiredRecords() {
        val now = DrivingInterferenceRetention.MAX_AGE_MILLIS + 1000
        val values = listOf(record(0, "old"), record(now, "current"), record(now + 1, "future"))
        val kept = DrivingInterferenceRetention.retained(values, now)
        assertEquals(1, kept.size)
        assertEquals(now, kept.single().utcEpochMillis)
    }

    @Test fun capsRecordCountDeterministically() {
        val values = (0..DrivingInterferenceRetention.MAX_RECORDS).map { record(it.toLong(), "p$it") }
        val kept = DrivingInterferenceRetention.retained(values, DrivingInterferenceRetention.MAX_RECORDS.toLong())
        assertEquals(DrivingInterferenceRetention.MAX_RECORDS, kept.size)
        assertTrue(kept.none { it.utcEpochMillis == 0L })
    }
}
