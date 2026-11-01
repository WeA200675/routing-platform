package org.routingplatform.app.navigation

enum class NavigationTrainingDataSplit {
    Training,
    Validation,
    UnknownTest,
}

enum class NavigationTrainingPriority {
    Critical,
    High,
    Normal,
    Low,
}

enum class NavigationPromotionStage {
    Observe,
    Suggest,
    Simulator,
    Device,
    ProductionCandidate,
}

data class NavigationTrainingConfidence(
    val modelConfidence: Double,
    val dataQuality: Double,
    val analyticalAgreement: Double,
    val sampleSupport: Int,
) {
    init {
        require(modelConfidence in 0.0..1.0)
        require(dataQuality in 0.0..1.0)
        require(analyticalAgreement in 0.0..1.0)
        require(sampleSupport >= 0)
    }

    val combined: Double
        get() =
            modelConfidence * 0.45 +
                dataQuality * 0.25 +
                analyticalAgreement * 0.20 +
                (sampleSupport.coerceAtMost(100) / 100.0) * 0.10
}

data class NavigationTrainingCase(
    val id: String,
    val category: String,
    val priority: NavigationTrainingPriority,
    val split: NavigationTrainingDataSplit,
    val inputSummary: String,
    val expectedSummary: String,
    val observedSummary: String,
    val decisiveSignals: List<String> = emptyList(),
    val rejectedSignals: List<String> = emptyList(),
    val affectedCodeAreas: List<String> = emptyList(),
    val suggestedTests: List<String> = emptyList(),
    val reviewState: NavigationTrainingReviewState = NavigationTrainingReviewState.Proposed,
)

private val TRAINING_CASE_ID = Regex("[A-Za-z0-9._:-]{1,96}")

fun NavigationTrainingCase.validateForReview() {
    require(TRAINING_CASE_ID.matches(id)) { "Training case id is invalid." }
    require(category.isNotBlank() && category.length <= 96) { "Training case category is invalid." }
    require(inputSummary.isNotBlank() && expectedSummary.isNotBlank() && observedSummary.isNotBlank()) {
        "Training case summaries must be non-blank."
    }
    require(suggestedTests.isNotEmpty()) { "Every proposed training case needs a suggested test." }
}

enum class NavigationTrainingReviewState {
    Proposed,
    Accepted,
    Rejected,
    Deferred,
    NeedsInformation,
}

data class NavigationTrainingExplanation(
    val decisiveSignals: List<String>,
    val rejectedSignals: List<String>,
    val changedCodeAreas: List<String>,
    val improvedTests: List<String>,
)

data class NavigationTrainingScore(
    val accuracy: Double,
    val timingAccuracy: Double,
    val directionAccuracy: Double,
    val stability: Double,
) {
    val overall: Double
        get() =
            (accuracy + timingAccuracy + directionAccuracy + stability) / 4.0
}

data class NavigationTrainingBeforeAfter(
    val before: NavigationTrainingScore,
    val after: NavigationTrainingScore,
)

data class NavigationTrainingVariant(
    val id: String,
    val description: String,
    val score: NavigationTrainingScore,
    val passedUnknownTests: Int,
    val rollbackRequired: Boolean,
)

data class NavigationTrainingResourceSnapshot(
    val processedCases: Int,
    val queueSize: Int,
    val averageLatencyMs: Double,
    val memoryMb: Double?,
    val batteryPercent: Int?,
    val temperatureC: Double?,
    val cancelledBackgroundRuns: Int,
)

data class NavigationTrainingMetrics(
    val processedCases: Int = 0,
    val passedCases: Int = 0,
    val failedCases: Int = 0,
    val improveableFailures: Int = 0,
    val capacityLimitCases: Int = 0,
    val passRate: Double = 0.0,
    val averageProcessingMs: Double = 0.0,
    val learnedCapabilities: List<String> = emptyList(),
    val examples: List<NavigationTrainingExample> = emptyList(),
    val recentFailures: List<NavigationTrainingFailure> = emptyList(),
)

data class NavigationTrainingExample(
    val title: String,
    val input: String,
    val learned: String,
)

data class NavigationTrainingFailure(
    val testId: String,
    val reason: String,
    val nextAction: String,
)

data class NavigationTrainingDriftReport(
    val detected: Boolean,
    val affectedCategories: Set<String>,
    val reason: String?,
    val recommendedAction: String?,
)

data class NavigationTrainingReport(
    val confidence: NavigationTrainingConfidence,
    val beforeAfter: NavigationTrainingBeforeAfter,
    val variants: List<NavigationTrainingVariant>,
    val explanation: NavigationTrainingExplanation,
    val resource: NavigationTrainingResourceSnapshot,
    val drift: NavigationTrainingDriftReport,
    val promotionStage: NavigationPromotionStage,
    val proposedCases: List<NavigationTrainingCase> = emptyList(),
) {
    val canPromoteToSimulator: Boolean
        get() =
            confidence.combined >= 0.75 &&
                beforeAfter.after.overall >=
                    beforeAfter.before.overall &&
                variants.none { it.rollbackRequired } &&
                !drift.detected &&
                proposedCases.none { it.reviewState == NavigationTrainingReviewState.Proposed ||
                    it.reviewState == NavigationTrainingReviewState.NeedsInformation }

    val recommendedVariant: NavigationTrainingVariant?
        get() =
            variants
                .filterNot { it.rollbackRequired }
                .maxByOrNull { it.score.overall }
}

class NavigationTrainingReportBuilder {
    fun build(
        confidence: NavigationTrainingConfidence,
        beforeAfter: NavigationTrainingBeforeAfter,
        variants: List<NavigationTrainingVariant>,
        explanation: NavigationTrainingExplanation,
        resource: NavigationTrainingResourceSnapshot,
        drift: NavigationTrainingDriftReport,
        requestedStage: NavigationPromotionStage,
        proposedCases: List<NavigationTrainingCase> = emptyList(),
    ): NavigationTrainingReport {
        require(proposedCases.map { it.id }.distinct().size == proposedCases.size) {
            "Training case ids must be unique."
        }
        proposedCases.forEach { it.validateForReview() }
        val provisional =
            NavigationTrainingReport(
                confidence = confidence,
                beforeAfter = beforeAfter,
                variants = variants,
                explanation = explanation,
                resource = resource,
                drift = drift,
                promotionStage = NavigationPromotionStage.Observe,
                proposedCases = proposedCases.sortedWith(compareBy<NavigationTrainingCase> { it.reviewState != NavigationTrainingReviewState.Proposed }
                    .thenBy { it.priority.ordinal }
                    .thenBy { it.id }),
            )

        val stage =
            when {
                requestedStage.ordinal >
                    NavigationPromotionStage.Simulator.ordinal &&
                    !provisional.canPromoteToSimulator ->
                    NavigationPromotionStage.Suggest

                requestedStage.ordinal >
                    NavigationPromotionStage.Simulator.ordinal ->
                    requestedStage

                provisional.canPromoteToSimulator ->
                    requestedStage

                else ->
                    NavigationPromotionStage.Suggest
            }

        return provisional.copy(
            promotionStage = stage,
        )
    }
}
