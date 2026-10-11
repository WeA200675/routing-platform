package org.routingplatform.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrivingSecurityObserverTest {
    private class MemoryStore : DrivingInterferenceEvidenceStore {
        val values = mutableListOf<DrivingInterferenceEvidenceRecord>()
        override fun append(record: DrivingInterferenceEvidenceRecord) { values += record }
        override fun readForAuthenticatedAdmin(nowUtcEpochMillis: Long) = values.toList()
        override fun purgeExpired(nowUtcEpochMillis: Long) = Unit
    }

    private val event = AuthorizedDrivingSystemEvent(
        DrivingInterferenceKind.ConnectivityChange, 1000, 20, "android-connectivity",
        "network-changed".toByteArray(), "unchanged"
    )

    @Test fun ignoresEventsOutsideActiveNavigation() {
        val store = MemoryStore()
        val observer = DrivingSecurityObserver(store) { "pseudo" }
        assertNull(observer.record(false, "raw-session", event))
        assertEquals(0, store.values.size)
    }

    @Test fun ordinaryConnectivityIsRecordedOnlyAndSessionIsPseudonymous() {
        val store = MemoryStore()
        val observer = DrivingSecurityObserver(store) { "pseudo-session" }
        val value = observer.record(true, "raw-session", event)!!
        assertEquals(DrivingInterferenceKind.ConnectivityChange, value.kind)
        assertEquals("record-only", value.action)
        assertEquals("pseudo-session", value.sessionReference)
        assertEquals(1, store.values.size)
    }

    @Test fun anomalyContainmentIsLocalNotHackBack() {
        val store = MemoryStore()
        val observer = DrivingSecurityObserver(store) { "pseudo" }
        val anomaly = event.copy(kind = DrivingInterferenceKind.InterferenceAnomaly)
        assertEquals("contain-local", observer.record(true, "s", anomaly)!!.action)
    }
}
