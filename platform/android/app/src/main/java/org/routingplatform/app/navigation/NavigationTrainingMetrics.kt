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
    val failedCases: Int = 0,
    val improveableFailures: Int = 0,
    val capacityLimitCases: Int = 0,
    val averageProcessingMs: Double = 0.0,
    val learnedCapabilities: Set<String> = emptySet(),
    val recentFailures: List<NavigationTrainingFailure> = emptyList(),
    val examples: List<NavigationTrainingExample> = emptyList(),
) {
    val passRate: Double
        get() =
            if (processedCases == 0) 0.0
            else passedCases.toDouble() / processedCases.toDouble()

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
        copy(capacityLimitCases = capacityLimitCases + 1)

    companion object {
        private const val MAX_FAILURES = 8
        private const val MAX_EXAMPLES = 8
    }
}
