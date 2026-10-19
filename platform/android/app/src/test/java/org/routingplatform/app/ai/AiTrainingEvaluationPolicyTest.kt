package org.routingplatform.app.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTrainingEvaluationPolicyTest {
    @Test fun requiresEveryExpectedTerm() {
        assertTrue(AiTrainingEvaluationPolicy.passes("In 300 Metern rechts abbiegen.", listOf("300", "rechts")))
        assertFalse(AiTrainingEvaluationPolicy.passes("In 300 Metern links abbiegen.", listOf("300", "rechts")))
    }

    @Test fun rejectsBlankOutputAndEmptyCriteria() {
        assertFalse(AiTrainingEvaluationPolicy.passes("", listOf("rechts")))
        assertFalse(AiTrainingEvaluationPolicy.passes("Fahre rechts.", emptyList()))
    }

    @Test fun matchingIsCaseInsensitive() {
        assertTrue(AiTrainingEvaluationPolicy.passes("Folge der AUSFAHRT A92.", listOf("ausfahrt", "a92")))
    }
}
