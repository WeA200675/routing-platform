package org.routingplatform.app.ai

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteFeedbackQuestionPolicyTest {
    @Test
    fun questionChangesAcrossCompletedDrives() {
        val first = RouteFeedbackQuestionPolicy.next(0, "Fastest", 18)
        val second = RouteFeedbackQuestionPolicy.next(first.first, "Fastest", 22)
        assertNotEquals(first.first, second.first)
    }

    @Test
    fun questionHasUsefulChoices() {
        val (_, question) = RouteFeedbackQuestionPolicy.next(2, "Comfort", 45)
        assertTrue(question.prompt.isNotBlank())
        assertTrue(question.options.size >= 4)
    }
}
