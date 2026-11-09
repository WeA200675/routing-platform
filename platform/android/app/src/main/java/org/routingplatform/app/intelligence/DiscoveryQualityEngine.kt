package org.routingplatform.app.intelligence

data class DiscoveryEvidence(
    val schemaValid: Boolean,
    val tlsVerified: Boolean,
    val provenanceVerified: Boolean,
    val responseLatencyMs: Long?,
    val sampleCount: Int,
    val freshnessSeconds: Long?,
    val errorRate: Double,
    val signatureVerified: Boolean = false,
) {
    init {
        require(responseLatencyMs == null || responseLatencyMs >= 0)
        require(sampleCount >= 0)
        require(freshnessSeconds == null || freshnessSeconds >= 0)
        require(errorRate in 0.0..1.0)
    }
}

enum class DiscoveryActivationStage {
    PROPOSED,
    SHADOW,
    CANARY,
    ACTIVE,
    ROLLED_BACK,
}

data class DiscoverySourceScore(
    val candidateId: String,
    val total: Double,
    val quality: Double,
    val freshness: Double,
    val reliability: Double,
    val safety: Double,
    val coverage: Double,
    val stage: DiscoveryActivationStage,
)

internal class DiscoveryQualityEngine(
    private val minimumSampleCount: Int = 3,
) {
    fun score(
        candidate: DiscoveryCandidate,
        evidence: DiscoveryEvidence,
        stage: DiscoveryActivationStage = DiscoveryActivationStage.PROPOSED,
    ): DiscoverySourceScore {
        val quality = if (evidence.schemaValid) 1.0 else 0.0
        val freshness = when {
            evidence.freshnessSeconds == null -> 0.0
            evidence.freshnessSeconds <= 60 -> 1.0
            evidence.freshnessSeconds <= 900 -> 0.7
            else -> 0.2
        }
        val reliability = (1.0 - evidence.errorRate).coerceIn(0.0, 1.0)
        val safety = if (
            evidence.tlsVerified &&
            evidence.provenanceVerified &&
            evidence.signatureVerified
        ) 1.0 else 0.0
        val coverage = (evidence.sampleCount.toDouble() /
            minimumSampleCount.coerceAtLeast(1)).coerceIn(0.0, 1.0)
        val total = (
            quality * 0.25 +
                freshness * 0.20 +
                reliability * 0.20 +
                safety * 0.25 +
                coverage * 0.10
            ) * candidate.confidence
        return DiscoverySourceScore(
            candidateId = candidate.id,
            total = total,
            quality = quality,
            freshness = freshness,
            reliability = reliability,
            safety = safety,
            coverage = coverage,
            stage = stage,
        )
    }

    fun canEnterShadow(score: DiscoverySourceScore): Boolean =
        score.safety == 1.0 && score.quality == 1.0 && score.total >= 0.65

    fun canEnterCanary(score: DiscoverySourceScore): Boolean =
        score.total >= 0.80 && score.reliability >= 0.95

    fun canActivate(score: DiscoverySourceScore): Boolean =
        score.total >= 0.90 &&
            score.safety == 1.0 &&
            score.reliability >= 0.98 &&
            score.freshness >= 0.7
}
