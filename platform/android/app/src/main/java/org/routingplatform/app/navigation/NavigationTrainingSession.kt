package org.routingplatform.app.navigation

class NavigationTrainingSession {
    private val deferredCases =
        NavigationDeferredTrainingQueue()

    var lastRecoveryRun: NavigationRecoveryRun? = null
        private set

    var metrics: NavigationTrainingMetrics =
        NavigationTrainingMetrics()
        private set

    fun recordPassed(
        testId: String,
        processingMs: Double,
        capability: String,
        example: NavigationTrainingExample? = null,
    ) {
        metrics =
            metrics.record(
                passed = true,
                processingMs = processingMs,
                capability = capability,
                example = example,
            )
    }

    fun recordFailed(
        testId: String,
        reason: String,
        improveable: Boolean,
        nextAction: String,
        processingMs: Double,
        capability: String? = null,
        example: NavigationTrainingExample? = null,
    ) {
        metrics =
            metrics.record(
                passed = false,
                processingMs = processingMs,
                capability = capability,
                example = example,
                failure =
                    NavigationTrainingFailure(
                        testId = testId,
                        reason = reason,
                        improveable = improveable,
                        nextAction = nextAction,
                    ),
            )
    }

    fun recordCapacityLimit(
        case: NavigationDeferredTrainingCase,
    ) {
        deferredCases.defer(case)
        metrics =
            metrics.recordCapacityLimit()
    }

    fun deferredCaseCount(): Int =
        deferredCases.size()

    fun recoverDeferredCases(
        normalize: (String) -> List<String>,
    ): NavigationRecoveryRun? {
        val run =
            deferredCases.recover(normalize)
        lastRecoveryRun = run
        if (run != null) {
            metrics =
                metrics.recordRecovery(
                    recovered = run.requeuedCount,
                    remainingDeferred = deferredCases.size(),
                )
        }
        return run
    }

    fun reset() {
        metrics =
            NavigationTrainingMetrics()
    }
}
