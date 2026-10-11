package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBoundedWorkQueueTest {
    @Test fun queueNeverExceedsPendingWorkBudget() {
        val q = NavigationBoundedWorkQueue<String>(
            NavigationResourceBudget(maximumBufferedSamples = 4, maximumPendingWork = 2)
        )
        assertTrue(q.offer("a", 0).admitNewWork)
        assertTrue(q.offer("b", 0).admitNewWork)
        val rejected = q.offer("c", 0)
        assertFalse(rejected.admitNewWork)
        assertTrue(rejected.cancelQueuedWork)
        assertEquals(0, q.size)
        assertNull(q.poll())
    }

    @Test fun exhaustedSampleBudgetRejectsAndCancelsQueuedWork() {
        val q = NavigationBoundedWorkQueue<String>(
            NavigationResourceBudget(maximumBufferedSamples = 1, maximumPendingWork = 2)
        )
        assertTrue(q.offer("a", 1).admitNewWork)
        val rejected = q.offer("b", 2)
        assertFalse(rejected.admitNewWork)
        assertTrue(rejected.cancelQueuedWork)
        assertEquals(0, q.size)
    }

    @Test fun invalidResourceSnapshotFailsClosed() {
        val q = NavigationBoundedWorkQueue<String>()
        val rejected = q.offer("a", -1)
        assertFalse(rejected.admitNewWork)
        assertTrue(rejected.cancelQueuedWork)
        assertEquals(0, q.size)
    }
    @Test fun diagnosticsRecordPressureAndCanReset() {
        val q = NavigationBoundedWorkQueue<String>(
            NavigationResourceBudget(maximumBufferedSamples = 4, maximumPendingWork = 2)
        )
        q.offer("a", 0)
        q.offer("b", 0)
        q.offer("c", 0)

        val pressure = q.diagnostics()
        assertEquals(2, pressure.highWaterMark)
        assertEquals(2L, pressure.admittedWork)
        assertEquals(1L, pressure.rejectedWork)
        assertEquals(2L, pressure.cancelledWork)
        assertEquals(0, pressure.queuedWork)

        q.resetDiagnostics()
        val reset = q.diagnostics()
        assertEquals(0, reset.highWaterMark)
        assertEquals(0L, reset.admittedWork)
        assertEquals(0L, reset.rejectedWork)
        assertEquals(0L, reset.cancelledWork)
    }
}
