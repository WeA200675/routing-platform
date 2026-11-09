package org.routingplatform.app.intelligence

data class DiscoveryConsensus(
    val subjectKey: String,
    val agreeingSources: List<String>,
    val conflictingSources: List<String>,
    val independentGroups: Set<String>,
    val confidence: Double,
    val accepted: Boolean,
    val reason: String,
)

data class DiscoverySourceVote(
    val sourceId: String,
    val subjectKey: String,
    val value: String,
    val score: Double,
    val stage: DiscoveryActivationStage,
    val independenceGroup: String = sourceId,
    val capturedAtEpochMs: Long = 0L,
)

internal class DiscoveryConsensusEngine(
    private val minimumAgreement: Int = 2,
    private val minimumConfidence: Double = 0.70,
    private val maxObservationSkewMs: Long = 120_000L,
) {
    fun evaluate(votes: List<DiscoverySourceVote>): List<DiscoveryConsensus> =
        votes.groupBy { it.subjectKey }.map { (subject, subjectVotes) ->
            val now = subjectVotes.maxOfOrNull { it.capturedAtEpochMs } ?: 0L
            val recent = subjectVotes.filter {
                now == 0L || now - it.capturedAtEpochMs <= maxObservationSkewMs
            }
            val groups = recent.groupBy { it.value }
            val winner = groups.maxByOrNull { (_, values) ->
                values.map { it.score.coerceIn(0.0, 1.0) }
                    .distinctBy { it }
                    .sum()
            }
            val selected = winner?.key
            val agreeing = winner?.value.orEmpty()
            val agreeingGroups = agreeing.map { it.independenceGroup }.toSet()
            val agreeingIds = agreeing.map { it.sourceId }
            val conflictingIds = recent
                .filter { it.sourceId !in agreeingIds }
                .map { it.sourceId }
            val confidence = agreeing
                .map { it.score.coerceIn(0.0, 1.0) }
                .average()
                .takeIf { it.isFinite() } ?: 0.0
            val accepted = selected != null &&
                agreeingGroups.size >= minimumAgreement &&
                confidence >= minimumConfidence &&
                agreeing.all {
                    it.stage == DiscoveryActivationStage.ACTIVE ||
                        it.stage == DiscoveryActivationStage.CANARY
                }
            DiscoveryConsensus(
                subjectKey = subject,
                agreeingSources = agreeingIds,
                conflictingSources = conflictingIds,
                independentGroups = agreeingGroups,
                confidence = confidence,
                accepted = accepted,
                reason = when {
                    selected == null -> "Keine verwertbare Quelle"
                    accepted -> "Zeitnaher Konsens unabhängiger Quellen für Wert $selected"
                    agreeingGroups.size < minimumAgreement -> "Zu wenige unabhängige Quellen"
                    confidence < minimumConfidence -> "Konsenskonfidenz zu niedrig"
                    else -> "Quellenstatus nicht ausreichend"
                },
            )
        }
}
