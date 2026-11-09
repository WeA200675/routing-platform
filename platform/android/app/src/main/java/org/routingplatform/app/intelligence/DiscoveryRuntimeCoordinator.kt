package org.routingplatform.app.intelligence

import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class DiscoveryTrainingRecord(
    val eventId: String,
    val candidateId: String,
    val kind: DiscoveryCandidateKind,
    val stage: DiscoveryActivationStage,
    val score: Double,
    val reliability: Double,
    val freshness: Double,
    val safety: Double,
    val latencyMs: Long,
    val success: Boolean,
    val reason: String,
    val capturedAtEpochMs: Long,
)

internal class DiscoveryRuntimeCoordinator(
    private val trustedHosts: Set<String>,
    private val onTrainingRecord: (DiscoveryTrainingRecord) -> Unit,
) {
    private val candidates = ConcurrentHashMap<String, DiscoveryCandidate>()
    private val lifecycle = DiscoveryLifecycleEngine()
    private val quality = DiscoveryQualityEngine()

    fun register(candidate: DiscoveryCandidate): DiscoveryEvaluation {
        candidates[candidate.id] = candidate
        return TrainableDiscoveryRegistry(trustedHosts).evaluate(candidate)
    }

    fun observe(
        providerId: String,
        capturedAtEpochMs: Long,
        latencyMs: Long,
        success: Boolean,
        eventCount: Int,
        freshEventCount: Int,
        schemaValid: Boolean,
        signatureValid: Boolean,
        error: String? = null,
    ): DiscoveryRuntimeDecision? {
        val candidate = candidates[providerId] ?: return null
        val observation = DiscoveryObservation(
            timestampEpochMs = capturedAtEpochMs,
            latencyMs = latencyMs,
            success = success,
            schemaValid = schemaValid,
            signatureValid = signatureValid,
            eventCount = eventCount,
            freshEventCount = freshEventCount,
            errorCode = error,
        )
        val evidence = DiscoveryEvidence(
            schemaValid = schemaValid,
            tlsVerified = candidate.endpoint.scheme == "https",
            provenanceVerified = candidate.source.isNotBlank(),
            responseLatencyMs = latencyMs,
            sampleCount = eventCount,
            freshnessSeconds = 0L.takeIf { freshEventCount > 0 },
            errorRate = if (success) 0.0 else 1.0,
            signatureVerified = signatureValid,
        )
        val decision = lifecycle.observe(candidate, evidence, observation)
        onTrainingRecord(
            DiscoveryTrainingRecord(
                eventId = providerId + "-" + capturedAtEpochMs,
                candidateId = providerId,
                kind = candidate.kind,
                stage = decision.stage,
                score = decision.score.total,
                reliability = decision.score.reliability,
                freshness = decision.score.freshness,
                safety = decision.score.safety,
                latencyMs = latencyMs,
                success = success,
                reason = decision.reason,
                capturedAtEpochMs = capturedAtEpochMs,
            ),
        )
        return decision
    }

    fun stage(candidateId: String): DiscoveryActivationStage =
        lifecycle.stage(candidateId)

    fun auditTrail(): List<DiscoveryAuditEntry> =
        lifecycle.auditTrail()
}
