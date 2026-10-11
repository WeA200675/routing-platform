package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidNavigationStateStoreTest {
    private lateinit var storage: FakeNavigationStateStorage
    private lateinit var store: AndroidNavigationStateStore

    @Before fun setUp() {
        storage = FakeNavigationStateStorage()
        store = AndroidNavigationStateStore(storage)
    }

    @Test fun roundTripRequiresSameBootAndFreshState() {
        val state = PersistedNavigationState(
            schemaVersion = NavigationStateRestoreAdmission.SCHEMA_VERSION,
            sessionId = "session-1",
            bootId = "boot-1",
            savedAtElapsedRealtimeNanos = 100L,
        )
        assertTrue(store.save(state))
        assertTrue(store.restore("boot-1", 150L, 100L) == state)
    }

    @Test fun crossBootRestoreFailsClosedAndClearsRecord() {
        assertTrue(store.save(PersistedNavigationState(1, "session-1", "boot-1", 100L)))
        assertNull(store.restore("boot-2", 150L, 100L))
        assertNull(store.restore("boot-1", 150L, 100L))
        assertTrue(storage.clearCalls == 1)
    }

    @Test fun staleRestoreFailsClosed() {
        assertTrue(store.save(PersistedNavigationState(1, "session-1", "boot-1", 100L)))
        assertNull(store.restore("boot-1", 1000L, 100L))
        assertTrue(storage.clearCalls == 1)
    }

    @Test fun unsafeDelimitedIdentityIsRejectedBeforePersistence() {
        assertFalse(store.save(PersistedNavigationState(1, "session|bad", "boot-1", 100L)))
        assertFalse(store.save(PersistedNavigationState(1, "session-1", "boot\nbad", 100L)))
        assertTrue(storage.writeCalls == 0)
    }

    @Test fun incompatibleSchemaIsRejectedBeforePersistence() {
        assertFalse(store.save(PersistedNavigationState(2, "session-1", "boot-1", 100L)))
        assertTrue(storage.writeCalls == 0)
    }

    @Test fun storageWriteFailureFailsClosed() {
        storage.writeSucceeds = false
        assertFalse(store.save(PersistedNavigationState(1, "session-1", "boot-1", 100L)))
    }

    @Test fun corruptRecordIsCleared() {
        storage.value = "not|a|valid|timestamp"
        assertNull(store.restore("boot-1", 100L, 100L))
        assertTrue(storage.clearCalls == 1)
    }
}

private class FakeNavigationStateStorage : NavigationStateStorage {
    var value: String? = null
    var writeSucceeds = true
    var writeCalls = 0
    var clearCalls = 0

    override fun read(): String? = value

    override fun write(value: String): Boolean {
        writeCalls += 1
        if (!writeSucceeds) return false
        this.value = value
        return true
    }

    override fun clear(): Boolean {
        clearCalls += 1
        value = null
        return true
    }
}
