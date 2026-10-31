package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTrainingPromotionGateTest {
    private fun report(caseState: NavigationTrainingReviewState = NavigationTrainingReviewState.Accepted, drift: Boolean = false): NavigationTrainingReport {
        val c = NavigationTrainingCase("c1", "gps", NavigationTrainingPriority.Critical, NavigationTrainingDataSplit.Validation, "in", "out", "obs", suggestedTests = listOf("t"), reviewState = caseState)
        val score = NavigationTrainingScore(1.0, 1.0, 1.0, 1.0)
        return NavigationTrainingReport(NavigationTrainingConfidence(1.0, 1.0, 1.0, 20), NavigationTrainingBeforeAfter(score, score), listOf(NavigationTrainingVariant("v", "safe", score, 2, false)), NavigationTrainingExplanation(emptyList(), emptyList(), emptyList(), listOf("t")), NavigationTrainingResourceSnapshot(1, 0, 10.0, 100.0, null, null, 0), NavigationTrainingDriftReport(drift, setOf("gps"), if (drift) "test" else null, null), NavigationPromotionStage.Simulator, listOf(c))
    }

    @Test fun unreviewedCaseHoldsPromotion() {
        assertEquals(NavigationTrainingPromotionDecision.Hold, NavigationTrainingPromotionGate.evaluate(report(NavigationTrainingReviewState.Proposed)).decision)
    }

    @Test fun driftHoldsPromotion() {
        assertEquals(NavigationTrainingPromotionDecision.Hold, NavigationTrainingPromotionGate.evaluate(report(drift = true)).decision)
    }

    @Test fun cleanReportPromotes() {
        assertEquals(NavigationTrainingPromotionDecision.Promote, NavigationTrainingPromotionGate.evaluate(report()).decision)
    }
}
