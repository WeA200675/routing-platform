package org.routingplatform.app.intelligence

import java.net.URI

enum class DiscoveryCandidateKind {
    LIVE_PROVIDER,
    ROUTING_ENGINE,
    POI_SOURCE,
    FUEL_PRICE_SOURCE,
    WEATHER_SOURCE,
    TRAINING_ARTIFACT,
}

data class DiscoveryCandidate(
    val id: String,
    val kind: DiscoveryCandidateKind,
    val endpoint: URI,
    val source: String,
    val discoveredAtEpochMs: Long,
    val evidence: List<String>,
    val confidence: Double,
) {
    init {
        require(id.isNotBlank())
        require(endpoint.scheme == "https")
        require(endpoint.host != null)
        require(confidence in 0.0..1.0)
    }
}

enum class DiscoveryDecision {
    ACCEPTED_FOR_REVIEW,
    REJECTED_INSECURE,
    REJECTED_UNTRUSTED,
    REJECTED_INVALID,
}

data class DiscoveryEvaluation(
    val candidate: DiscoveryCandidate,
    val decision: DiscoveryDecision,
    val reasons: List<String>,
)

/**
 * Shared governance for all future source discovery.
 *
 * Discovery may learn and propose new sources, but activation remains explicit
 * and auditable. This prevents an external feed from silently changing routing,
 * training, POI, price or artifact behavior.
 */
internal class TrainableDiscoveryRegistry(
    private val trustedHosts: Set<String>,
    private val minConfidence: Double = 0.75,
) {
    private val proposals = mutableListOf<DiscoveryEvaluation>()

    @Synchronized
    fun evaluate(candidate: DiscoveryCandidate): DiscoveryEvaluation {
        val reasons = buildList {
            if (candidate.endpoint.scheme != "https") add("HTTPS erforderlich")
            if (candidate.endpoint.host !in trustedHosts) add("Host nicht vertrauenswürdig")
            if (candidate.confidence < minConfidence) add("Konfidenz unter Mindestwert")
            if (candidate.evidence.isEmpty()) add("Keine prüfbare Evidenz")
        }
        val decision = when {
            reasons.any { it == "HTTPS erforderlich" } -> DiscoveryDecision.REJECTED_INSECURE
            reasons.any { it == "Host nicht vertrauenswürdig" } -> DiscoveryDecision.REJECTED_UNTRUSTED
            reasons.isNotEmpty() -> DiscoveryDecision.REJECTED_INVALID
            else -> DiscoveryDecision.ACCEPTED_FOR_REVIEW
        }
        return DiscoveryEvaluation(candidate, decision, reasons).also { proposals += it }
    }

    @Synchronized
    fun pendingReviews(): List<DiscoveryEvaluation> =
        proposals.filter { it.decision == DiscoveryDecision.ACCEPTED_FOR_REVIEW }.toList()

    @Synchronized
    fun clear() {
        proposals.clear()
    }
}
