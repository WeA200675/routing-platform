package org.routingplatform.app.intelligence

data class DiscoveryObservation(
    val timestampEpochMs: Long,
    val latencyMs: Long?,
    val success: Boolean,
    val schemaValid: Boolean,
    val signatureValid: Boolean,
    val eventCount: Int,
    val freshEventCount: Int,
    val errorCode: String? = null,
) {
    init {
        require(timestampEpochMs >= 0)
        require(latencyMs == null || latencyMs >= 0)
        require(eventCount >= 0)
        require(freshEventCount in 0..eventCount)
    }
}

data class DiscoveryRuntimeDecision(
    val candidateId: String,
    val stage: DiscoveryActivationStage,
    val score: DiscoverySourceScore,
    val reason: String,
    val quarantined: Boolean,
)

data class DiscoveryAuditEntry(
    val candidateId: String,
    val timestampEpochMs: Long,
    val previousStage: DiscoveryActivationStage,
    val nextStage: DiscoveryActivationStage,
    val reason: String,
    val score: Double,
)

internal class DiscoveryLifecycleEngine(
    private val qualityEngine: DiscoveryQualityEngine = DiscoveryQualityEngine(),
    private val windowSize: Int = 20,
    private val quarantineErrorRate: Double = 0.40,
) {
    private val observations = mutableMapOf<String, ArrayDeque<DiscoveryObservation>>()
    private val stages = mutableMapOf<String, DiscoveryActivationStage>()
    private val audit = ArrayDeque<DiscoveryAuditEntry>()

    @Synchronized
    fun observe(
        candidate: DiscoveryCandidate,
        evidence: DiscoveryEvidence,
        observation: DiscoveryObservation,
    ): DiscoveryRuntimeDecision {
        val history = observations.getOrPut(candidate.id) { ArrayDeque() }
        history.addLast(observation)
        while (history.size > windowSize) history.removeFirst()

        val recent = history.toList()
        val errors = recent.count { !it.success }.toDouble() / recent.size
        val successSamples = recent.filter { it.success }
        val aggregate = evidence.copy(
            sampleCount = successSamples.sumOf { it.freshEventCount },
            errorRate = errors,
            schemaValid = successSamples.isNotEmpty() && successSamples.all { it.schemaValid },
            signatureVerified = successSamples.isNotEmpty() && successSamples.all { it.signatureValid },
            freshnessSeconds = if (successSamples.isEmpty()) null else 0L,
        )
        val oldStage = stages[candidate.id] ?: DiscoveryActivationStage.PROPOSED
        val score = qualityEngine.score(candidate, aggregate, oldStage)

        val next = when {
            errors >= quarantineErrorRate ||
                !aggregate.signatureVerified ||
                !aggregate.schemaValid ->
                DiscoveryActivationStage.ROLLED_BACK
            oldStage == DiscoveryActivationStage.PROPOSED &&
                qualityEngine.canEnterShadow(score) ->
                DiscoveryActivationStage.SHADOW
            oldStage == DiscoveryActivationStage.SHADOW &&
                qualityEngine.canEnterCanary(score) &&
                recent.size >= windowSize / 2 ->
                DiscoveryActivationStage.CANARY
            oldStage == DiscoveryActivationStage.CANARY &&
                qualityEngine.canActivate(score) &&
                recent.size >= windowSize ->
                DiscoveryActivationStage.ACTIVE
            oldStage == DiscoveryActivationStage.ACTIVE &&
                (score.reliability < 0.90 || score.safety < 1.0) ->
                DiscoveryActivationStage.ROLLED_BACK
            else -> oldStage
        }

        if (next != oldStage) {
            stages[candidate.id] = next
            audit.addLast(
                DiscoveryAuditEntry(
                    candidateId = candidate.id,
                    timestampEpochMs = observation.timestampEpochMs,
                    previousStage = oldStage,
                    nextStage = next,
                    reason = stageReason(oldStage, next, score),
                    score = score.total,
                ),
            )
            while (audit.size > 200) audit.removeFirst()
        }

        return DiscoveryRuntimeDecision(
            candidateId = candidate.id,
            stage = next,
            score = score.copy(stage = next),
            reason = stageReason(oldStage, next, score),
            quarantined = next == DiscoveryActivationStage.ROLLED_BACK,
        )
    }

    @Synchronized
    fun stage(candidateId: String): DiscoveryActivationStage =
        stages[candidateId] ?: DiscoveryActivationStage.PROPOSED

    @Synchronized
    fun auditTrail(): List<DiscoveryAuditEntry> = audit.toList()

    private fun stageReason(
        previous: DiscoveryActivationStage,
        next: DiscoveryActivationStage,
        score: DiscoverySourceScore,
    ): String =
        if (previous == next) {
            "Keine Stufenänderung; Score=" + score.total
        } else {
            previous.toString() + " -> " + next + "; Score=" + score.total
        }
}
