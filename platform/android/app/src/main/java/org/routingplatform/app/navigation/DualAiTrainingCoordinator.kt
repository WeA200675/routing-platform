package org.routingplatform.app.navigation

/**
 * Runs local navigation decisions immediately and submits the same immutable
 * decision context to the VM as a shadow-training request. The VM can advise,
 * but only the local safety decision can activate navigation.
 */
internal class DualAiTrainingCoordinator(
    private val cloud: CloudTrainingShadow = CloudTrainingShadow.NOOP,
) {
    fun evaluate(
        nowEpochMs: Long,
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
    ): DualAiTrainingResult {
        val cloudAdvice = cloud.evaluate(twin, local)
        val arbitration = DualAiDecisionArbitrator.arbitrate(
            nowEpochMs = nowEpochMs,
            twin = twin,
            local = local,
            cloud = cloudAdvice,
        )
        cloud.recordOutcome(
            twin = twin,
            local = local,
            cloud = cloudAdvice,
            accepted = arbitration.accepted,
            rejection = arbitration.rejection,
        )
        return DualAiTrainingResult(
            accepted = arbitration.accepted,
            cloudAdvice = cloudAdvice,
            rejection = arbitration.rejection,
        )
    }
}

internal data class DualAiTrainingResult(
    val accepted: NavigationDecisionEnvelope?,
    val cloudAdvice: NavigationDecisionEnvelope?,
    val rejection: DecisionRejection?,
)

internal interface CloudTrainingShadow {
    fun evaluate(
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
    ): NavigationDecisionEnvelope?

    fun recordOutcome(
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
        cloud: NavigationDecisionEnvelope?,
        accepted: NavigationDecisionEnvelope?,
        rejection: DecisionRejection?,
    )

    companion object {
        val NOOP: CloudTrainingShadow = object : CloudTrainingShadow {
            override fun evaluate(
                twin: NavigationTwinState,
                local: NavigationDecisionEnvelope,
            ): NavigationDecisionEnvelope? = null

            override fun recordOutcome(
                twin: NavigationTwinState,
                local: NavigationDecisionEnvelope,
                cloud: NavigationDecisionEnvelope?,
                accepted: NavigationDecisionEnvelope?,
                rejection: DecisionRejection?,
            ) = Unit
        }
    }
}
