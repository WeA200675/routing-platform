package org.routingplatform.app.navigation

enum class NavigationTrainingPromotionDecision {
    Promote,
    Hold,
    Rollback,
}

data class NavigationTrainingPromotionResult(
    val decision: NavigationTrainingPromotionDecision,
    val reasons: List<String>,
) {
    init { require(reasons.isNotEmpty()) }
}

/** Fail-closed release boundary for learned policy variants. */
object NavigationTrainingPromotionGate {
    fun evaluate(
        report: NavigationTrainingReport,
        minimumUnknownTests: Int = 1,
        maximumLatencyMs: Double = 2_000.0,
        maximumMemoryMb: Double = 1_024.0,
    ): NavigationTrainingPromotionResult {
        require(minimumUnknownTests >= 0)
        require(maximumLatencyMs.isFinite() && maximumLatencyMs > 0.0)
        require(maximumMemoryMb.isFinite() && maximumMemoryMb > 0.0)
        val reasons = mutableListOf<String>()
        if (report.drift.detected) reasons += "Drift erkannt: ${report.drift.reason ?: "unbekannte Ursache"}"
        if (report.proposedCases.any { it.reviewState == NavigationTrainingReviewState.Proposed || it.reviewState == NavigationTrainingReviewState.NeedsInformation }) {
            reasons += "Mindestens ein Trainingsfall ist ungeprüft."
        }
        if (report.proposedCases.any { it.priority == NavigationTrainingPriority.Critical && it.reviewState != NavigationTrainingReviewState.Accepted }) {
            reasons += "Ein kritischer Trainingsfall wurde nicht übernommen."
        }
        if (report.variants.any { it.rollbackRequired }) reasons += "Eine Variante verlangt Rollback."
        if (report.variants.none { it.passedUnknownTests >= minimumUnknownTests }) reasons += "Kein unbekannter Test bestanden."
        if (report.resource.averageLatencyMs > maximumLatencyMs) reasons += "Latenzgrenze überschritten."
        if ((report.resource.memoryMb ?: 0.0) > maximumMemoryMb) reasons += "Speichergrenze überschritten."
        if (report.beforeAfter.after.overall < report.beforeAfter.before.overall) reasons += "Gesamtscore verschlechtert."
        if (reasons.isNotEmpty()) return NavigationTrainingPromotionResult(NavigationTrainingPromotionDecision.Hold, reasons)
        return NavigationTrainingPromotionResult(NavigationTrainingPromotionDecision.Promote, listOf("Alle automatischen Promotion-Gates bestanden."))
    }
}
