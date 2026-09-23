package org.routingplatform.app.navigation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidNavigationStateStoreTest {
    private lateinit var context: Context
    private lateinit var store: AndroidNavigationStateStore

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("navigation_resume_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = AndroidNavigationStateStore(context)
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
    }

    @Test fun staleRestoreFailsClosed() {
        assertTrue(store.save(PersistedNavigationState(1, "session-1", "boot-1", 100L)))
        assertNull(store.restore("boot-1", 1000L, 100L))
    }

    @Test fun unsafeDelimitedIdentityIsRejectedBeforePersistence() {
        assertFalse(store.save(PersistedNavigationState(1, "session|bad", "boot-1", 100L)))
        assertFalse(store.save(PersistedNavigationState(1, "session-1", "boot\nbad", 100L)))
    }

    @Test fun incompatibleSchemaIsRejectedBeforePersistence() {
        assertFalse(store.save(PersistedNavigationState(2, "session-1", "boot-1", 100L)))
    }
}
