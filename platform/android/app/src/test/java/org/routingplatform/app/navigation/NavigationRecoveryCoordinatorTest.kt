package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NavigationRecoveryCoordinatorTest {
    @Test
    fun createsOnlyOneRequestWhileRecoveryIsPending() {
        val coordinator = NavigationRecoveryCoordinator()
        val first = coordinator.evaluate(20_000, 1_000, 1_000, true)
        val second = coordinator.evaluate(21_000, 1_000, 1_000, true)

        assertEquals(NavigationRecoveryAction.REQUEST_RECALCULATION, first.action)
        assertEquals(NavigationRecoveryAction.KEEP_CURRENT_ROUTE, second.action)
        assertEquals(first.requestId, second.requestId)
        assertNotNull(first.requestId)
    }

    @Test
    fun allowsNewRequestAfterCompletion() {
        val coordinator = NavigationRecoveryCoordinator()
        val first = coordinator.evaluate(20_000, 1_000, 1_000, true)
        coordinator.complete(first.requestId!!)
        val second = coordinator.evaluate(40_000, 1_000, 1_000, true)

        assertEquals(NavigationRecoveryAction.REQUEST_RECALCULATION, second.action)
        assertEquals(2L, second.requestId)
    }
}
