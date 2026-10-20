package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test fun prioritizesWeakAndUnseenCases() {
        val cases = listOf(
            TrainingCase("Fall stabil", listOf("ok"), "stabil"),
            TrainingCase("Fall schwach", listOf("ok"), "schwach"),
        )
        val results = (1L..5L).map { AiTrainingResult("maneuvers", "stabil", 100, true, it) } +
            (6L..10L).map { AiTrainingResult("maneuvers", "schwach", 100, false, it) }
        assertEquals("schwach", AiTrainingPrioritizer.nextCase(AiTrainingTrack.Maneuvers, cases, results).label)
    }

    @Test fun validatesGeneratedCaseAndRejectsCoordinatesAndDuplicates() {
        val json = """{"label":"Ausfahrt sicher ansagen","prompt":"Formuliere eine sichere Ausfahrt-Ansage und verwende ausdrücklich rechts sowie Ausfahrt.","requiredTerms":["rechts","Ausfahrt"]}"""
        val candidate = AiTrainingCaseReview.parseAndScore(json, AiTrainingTrack.Maneuvers, emptyList(), 1L)
        assertNotNull(candidate)
        assertTrue(candidate!!.qualityScore >= 80)
        val unsafe = """{"label":"Testfall GPS","prompt":"Erkläre GPS Position 48.123456, 11.123456 und verwende GPS.","requiredTerms":["GPS","Position"]}"""
        assertFalse(AiTrainingCaseReview.parseAndScore(unsafe, AiTrainingTrack.GpsSafety, emptyList(), 2L) != null)
        assertFalse(AiTrainingCaseReview.parseAndScore(json, AiTrainingTrack.Maneuvers, listOf(candidate), 3L) != null)
    }
}
