package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationResourceGovernorTest {
    private val budget = NavigationResourceBudget(4, 2)

    @Test fun boundaryIsAvailable() {
        assertEquals(
            NavigationResourceAdmission.Available,
            NavigationResourceGovernor.admit(NavigationResourceSnapshot(4, 2), budget),
        )
    }

    @Test fun pressureFailsClosed() {
        assertEquals(
            NavigationResourceAdmission.Exhausted,
            NavigationResourceGovernor.admit(NavigationResourceSnapshot(5, 2), budget),
        )
        assertEquals(
            NavigationResourceAdmission.Exhausted,
            NavigationResourceGovernor.admit(NavigationResourceSnapshot(4, 3), budget),
        )
    }

    @Test fun malformedCountersFailClosed() {
        assertEquals(
            NavigationResourceAdmission.Invalid,
            NavigationResourceGovernor.admit(NavigationResourceSnapshot(-1, 0), budget),
        )
    }
    @Test fun backpressureRejectsAndCancelsOnPressureOrInvalidState() {
        val available = NavigationResourceAdmission.Available.toBackpressureDecision()
        assertEquals(true, available.admitNewWork)
        assertEquals(false, available.cancelQueuedWork)

        for (admission in listOf(
            NavigationResourceAdmission.Exhausted,
            NavigationResourceAdmission.Invalid,
        )) {
            val decision = admission.toBackpressureDecision()
            assertEquals(false, decision.admitNewWork)
            assertEquals(true, decision.cancelQueuedWork)
        }
    }
}
