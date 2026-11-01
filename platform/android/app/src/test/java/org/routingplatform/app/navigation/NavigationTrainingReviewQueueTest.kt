package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTrainingReviewQueueTest {
    private fun case(id: String, priority: NavigationTrainingPriority = NavigationTrainingPriority.Normal) =
        NavigationTrainingCase(id, "navigation", priority, NavigationTrainingDataSplit.Validation,
            "input", "expected", "observed", suggestedTests = listOf("$id-test"))

    @Test fun criticalProposalsComeFirstAndReviewIsExplicit() {
        val queue = NavigationTrainingReviewQueue(listOf(case("normal"), case("critical", NavigationTrainingPriority.Critical)))
        assertEquals("critical", queue.snapshot().first().id)
        assertTrue(queue.review("critical", NavigationTrainingReviewState.Accepted))
        assertFalse(queue.review("missing", NavigationTrainingReviewState.Accepted))
        assertEquals(NavigationTrainingReviewState.Accepted, queue.snapshot().first { it.id == "critical" }.reviewState)
    }

    @Test(expected = IllegalArgumentException::class)
    fun incompleteCaseIsRejected() {
        NavigationTrainingReviewQueue(listOf(case("bad").copy(suggestedTests = emptyList())))
    }
}
