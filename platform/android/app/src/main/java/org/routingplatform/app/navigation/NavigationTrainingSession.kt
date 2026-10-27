package org.routingplatform.app.navigation

class NavigationTrainingSession {
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

    fun recordCapacityLimit() {
        metrics =
            metrics.recordCapacityLimit()
    }

    fun reset() {
        metrics =
            NavigationTrainingMetrics()
    }
}
