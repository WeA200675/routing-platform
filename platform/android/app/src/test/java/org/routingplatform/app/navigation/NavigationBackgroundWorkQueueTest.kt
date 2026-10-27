package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationBackgroundWorkQueueTest {
    @Test
    fun navigationTaskEvictsQueuedAiWork() {
        val queue = NavigationBackgroundWorkQueue()
        queue.offer(
            NavigationBackgroundTask(
                "ai-1",
                NavigationBackgroundPriority.AiAnalysis,
            ) {},
        )
        queue.offer(
            NavigationBackgroundTask(
                "nav-1",
                NavigationBackgroundPriority.Navigation,
            ) {},
        )

        assertEquals(1, queue.size())
        assertEquals("nav-1", queue.runNext(navigationActive = true))
    }

    @Test
    fun aiWorkWaitsWhileNavigationIsActive() {
        val queue = NavigationBackgroundWorkQueue()
        queue.offer(
            NavigationBackgroundTask(
                "ai-1",
                NavigationBackgroundPriority.AiAnalysis,
            ) {},
        )

        assertEquals(null, queue.runNext(navigationActive = true))
        assertEquals("ai-1", queue.runNext(navigationActive = false))
    }
}
