package org.routingplatform.app.intelligence

data class DiscoveryConsensus(
    val subjectKey: String,
    val agreeingSources: List<String>,
    val conflictingSources: List<String>,
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
)

internal class DiscoveryConsensusEngine(
    private val minimumAgreement: Int = 2,
    private val minimumConfidence: Double = 0.70,
) {
    fun evaluate(votes: List<DiscoverySourceVote>): List<DiscoveryConsensus> =
        votes.groupBy { it.subjectKey }.map { (subject, subjectVotes) ->
            val groups = subjectVotes.groupBy { it.value }
            val winner = groups.maxByOrNull { (_, values) ->
                values.sumOf { it.score.coerceIn(0.0, 1.0) }
            }
            val agreeing = winner?.value.orEmpty()
            val selected = winner?.key
            val agreeingIds = winner?.value?.map { it.sourceId }.orEmpty()
            val conflictingIds = subjectVotes
                .filter { it.sourceId !in agreeingIds }
                .map { it.sourceId }
            val confidence = winner?.value
                ?.map { it.score.coerceIn(0.0, 1.0) }
                ?.average()
                ?: 0.0
            val accepted = selected != null &&
                agreeingIds.size >= minimumAgreement &&
                confidence >= minimumConfidence &&
                agreeingIds.all { id ->
                    subjectVotes.any {
                        it.sourceId == id &&
                            (it.stage == DiscoveryActivationStage.ACTIVE ||
                                it.stage == DiscoveryActivationStage.CANARY)
                    }
                }
            DiscoveryConsensus(
                subjectKey = subject,
                agreeingSources = agreeingIds,
                conflictingSources = conflictingIds,
                confidence = confidence,
                accepted = accepted,
                reason = when {
                    selected == null -> "Keine verwertbare Quelle"
                    accepted -> "Quellenkonsens für Wert $selected"
                    agreeingIds.size < minimumAgreement -> "Zu wenige unabhängige Quellen"
                    confidence < minimumConfidence -> "Konsenskonfidenz zu niedrig"
                    else -> "Quellenstatus nicht ausreichend"
                },
            )
        }
}
