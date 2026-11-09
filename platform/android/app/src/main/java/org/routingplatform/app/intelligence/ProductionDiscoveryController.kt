package org.routingplatform.app.intelligence

data class ProductionDiscoveryResult(
    val candidateId: String,
    val guard: DiscoveryGuardDecision,
    val runtime: DiscoveryRuntimeDecision?,
    val reputation: DiscoveryReputation?,
    val anomalies: List<DiscoveryAnomaly>,
    val acceptedForProduction: Boolean,
)

internal class ProductionDiscoveryController(
    allowedHosts: Set<String>,
    trustedManifestKeys: Set<String>,
    private val onTrainingRecord: (DiscoveryTrainingRecord) -> Unit,
) {
    private val guard = DiscoveryActivationGuard(
        allowedHosts = allowedHosts,
        trustedManifestKeys = trustedManifestKeys,
    )
    private val runtime = DiscoveryRuntimeCoordinator(
        trustedHosts = allowedHosts,
        onTrainingRecord = onTrainingRecord,
    )
    private val anomalies = DiscoveryAnomalyDetector()

    fun register(candidate: DiscoveryCandidate): DiscoveryEvaluation =
        runtime.register(candidate)

    fun observe(
        candidate: DiscoveryCandidate,
        manifestKeyId: String?,
        signatureVerified: Boolean,
        capturedAtEpochMs: Long,
        latencyMs: Long,
        success: Boolean,
        eventCount: Int,
        freshEventCount: Int,
        schemaValid: Boolean,
        error: String? = null,
    ): ProductionDiscoveryResult {
        val guardDecision = guard.authorize(
            candidate = candidate,
            manifestKeyId = manifestKeyId,
            signatureVerified = signatureVerified,
            nowEpochMs = capturedAtEpochMs,
        )
        if (!guardDecision.allowed) {
            return ProductionDiscoveryResult(
                candidateId = candidate.id,
                guard = guardDecision,
                runtime = null,
                reputation = runtime.reputation(candidate.id),
                anomalies = emptyList(),
                acceptedForProduction = false,
            )
        }

        val anomalyList = anomalies.observe(
            candidateId = candidate.id,
            latencyMs = latencyMs,
            errorRate = if (success) 0.0 else 1.0,
            freshnessSeconds = if (freshEventCount > 0) 0L else 901L,
            eventCount = eventCount,
        )
        val runtimeDecision = runtime.observe(
            providerId = candidate.id,
            capturedAtEpochMs = capturedAtEpochMs,
            latencyMs = latencyMs,
            success = success,
            eventCount = eventCount,
            freshEventCount = freshEventCount,
            schemaValid = schemaValid,
            signatureValid = signatureVerified,
            error = error,
        )
        val reputation = runtime.reputation(candidate.id)
        val accepted = runtimeDecision != null &&
            runtimeDecision.stage == DiscoveryActivationStage.ACTIVE &&
            !runtimeDecision.quarantined &&
            anomalyList.isEmpty()
        return ProductionDiscoveryResult(
            candidateId = candidate.id,
            guard = guardDecision,
            runtime = runtimeDecision,
            reputation = reputation,
            anomalies = anomalyList,
            acceptedForProduction = accepted,
        )
    }

    fun setKillSwitch(enabled: Boolean) {
        guard.setKillSwitch(enabled)
    }

    fun auditTrail(): List<DiscoveryAuditEntry> =
        runtime.auditTrail()
}
