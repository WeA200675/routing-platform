package org.routingplatform.app.navigation

data class NavigationTrainingFailure(
    val testId: String,
    val reason: String,
    val improveable: Boolean,
    val nextAction: String,
)

data class NavigationTrainingExample(
    val title: String,
    val input: String,
    val learned: String,
)

data class NavigationTrainingMetrics(
    val processedCases: Int = 0,
    val passedCases: Int = 0,
    val positiveRuns: Int = 0,
    val failedCases: Int = 0,
    val improveableFailures: Int = 0,
    val capacityLimitCases: Int = 0,
    val deferredCases: Int = 0,
    val recoveredCases: Int = 0,
    val averageProcessingMs: Double = 0.0,
    val learnedCapabilities: Set<String> =
        setOf(
            "GPS-Plausibilität",
            "Richtungsprüfung",
            "Ansagezeitpunkt",
        ),
    val recentFailures: List<NavigationTrainingFailure> = emptyList(),
    val examples: List<NavigationTrainingExample> =
        listOf(
            NavigationTrainingExample(
                "GPS-Sprung",
                "großer Positionssprung bei kurzer Zeit",
                "Messpunkt zurückhalten",
            ),
            NavigationTrainingExample(
                "Abbiegung",
                "Bearing passt nicht zur Route",
                "Richtung unsicher markieren",
            ),
        ),
) {
    val passRate: Double
        get() =
            if (processedCases == 0) 0.0
            else passedCases.toDouble() / processedCases.toDouble()

    val responseLengthTier: NavigationResponseLengthTier
        get() =
            NavigationResponseLengthTier.fromPositiveRuns(positiveRuns)

    val learnableRate: Double
        get() =
            if (failedCases == 0) 0.0
            else improveableFailures.toDouble() / failedCases.toDouble()

    fun record(
        passed: Boolean,
        processingMs: Double,
        failure: NavigationTrainingFailure? = null,
        capability: String? = null,
        example: NavigationTrainingExample? = null,
    ): NavigationTrainingMetrics {
        val nextFailures =
            if (failure == null) recentFailures
            else (listOf(failure) + recentFailures).take(MAX_FAILURES)

        val nextExamples =
            if (example == null) examples
            else (listOf(example) + examples).take(MAX_EXAMPLES)

        return copy(
            processedCases = processedCases + 1,
            passedCases = passedCases + if (passed) 1 else 0,
            positiveRuns = if (passed) positiveRuns + 1 else positiveRuns,
            failedCases = failedCases + if (passed) 0 else 1,
            improveableFailures =
                improveableFailures +
                    if (!passed && failure?.improveable == true) 1 else 0,
            averageProcessingMs =
                ((averageProcessingMs * processedCases) + processingMs) /
                    (processedCases + 1),
            learnedCapabilities =
                if (capability == null) learnedCapabilities
                else learnedCapabilities + capability,
            recentFailures = nextFailures,
            examples = nextExamples,
        )
    }

    fun recordCapacityLimit(): NavigationTrainingMetrics =
        copy(
            capacityLimitCases = capacityLimitCases + 1,
            deferredCases = deferredCases + 1,
        )

    fun recordRecovery(
        recovered: Int,
        remainingDeferred: Int,
    ): NavigationTrainingMetrics =
        copy(
            recoveredCases = recoveredCases + recovered,
            deferredCases = remainingDeferred,
        )

    companion object {
        private const val MAX_FAILURES = 8
        private const val MAX_EXAMPLES = 8
    }
}
