package org.routingplatform.app.ai

import org.routingplatform.app.profile.AiPreferences

enum class SocialAiLearningOrigin {
    ExplicitUser,
    UserCorrection,
    ConversationInference,
    BehaviorObservation,
}

data class SocialAiLearningCandidate(
    val id: String,
    val kind: SocialAiKnowledgeKind,
    val key: String,
    val value: String,
    val confidence: Double,
    val scope: SocialAiMemoryScope,
    val contextKey: String? = null,
    val source: String,
    val observedAtEpochMillis: Long,
    val origin: SocialAiLearningOrigin,
)

enum class SocialAiLearningDecisionReason {
    StoreExplicitUserMemory,
    StoreAutomaticLearning,
    RejectLearningDisabled,
    RejectLowConfidence,
}

data class SocialAiLearningDecision(
    val shouldStore: Boolean,
    val reason: SocialAiLearningDecisionReason,
    val knowledge: SocialAiKnowledge? = null,
)

object SocialAiLearningPolicy {

    const val DEFAULT_AUTOMATIC_CONFIDENCE_THRESHOLD =
        0.75

    fun evaluate(
        preferences: AiPreferences,
        candidate: SocialAiLearningCandidate,
        automaticConfidenceThreshold: Double =
            DEFAULT_AUTOMATIC_CONFIDENCE_THRESHOLD,
    ): SocialAiLearningDecision {
        require(
            automaticConfidenceThreshold.isFinite() &&
                automaticConfidenceThreshold in 0.0..1.0
        ) {
            "automaticConfidenceThreshold must be finite and in [0, 1]."
        }

        // Construct once up-front so all normal SocialAiKnowledge validation
        // applies equally to accepted and rejected candidates.
        val validatedKnowledge =
            SocialAiKnowledge(
                id = candidate.id,
                kind = candidate.kind,
                key = candidate.key,
                value = candidate.value,
                confidence = candidate.confidence,
                scope = candidate.scope,
                contextKey = candidate.contextKey,
                source = candidate.source,
                observedAtEpochMillis =
                    candidate.observedAtEpochMillis,
                userLocked =
                    candidate.origin ==
                        SocialAiLearningOrigin.ExplicitUser ||
                        candidate.origin ==
                        SocialAiLearningOrigin.UserCorrection,
            )

        val explicitUserMemory =
            candidate.origin ==
                SocialAiLearningOrigin.ExplicitUser ||
                candidate.origin ==
                SocialAiLearningOrigin.UserCorrection

        if (explicitUserMemory) {
            return SocialAiLearningDecision(
                shouldStore = true,
                reason =
                    SocialAiLearningDecisionReason
                        .StoreExplicitUserMemory,
                knowledge = validatedKnowledge,
            )
        }

        if (!preferences.learningEnabled) {
            return SocialAiLearningDecision(
                shouldStore = false,
                reason =
                    SocialAiLearningDecisionReason
                        .RejectLearningDisabled,
            )
        }

        if (
            candidate.confidence <
                automaticConfidenceThreshold
        ) {
            return SocialAiLearningDecision(
                shouldStore = false,
                reason =
                    SocialAiLearningDecisionReason
                        .RejectLowConfidence,
            )
        }

        return SocialAiLearningDecision(
            shouldStore = true,
            reason =
                SocialAiLearningDecisionReason
                    .StoreAutomaticLearning,
            knowledge = validatedKnowledge,
        )
    }
}

object SocialAiMemoryMergePolicy {

    fun merge(
        existing: List<SocialAiKnowledge>,
        incoming: SocialAiKnowledge,
    ): List<SocialAiKnowledge> {
        val sameIdIndex =
            existing.indexOfFirst {
                it.id == incoming.id
            }

        if (sameIdIndex >= 0) {
            val current =
                existing[sameIdIndex]

            if (current.userLocked && !incoming.userLocked) {
                return existing
            }

            return existing.toMutableList().apply {
                this[sameIdIndex] = incoming
            }
        }

        val sameSemanticIndex =
            existing.indexOfFirst {
                it.kind == incoming.kind &&
                    it.key == incoming.key &&
                    it.scope == incoming.scope &&
                    it.contextKey == incoming.contextKey
            }

        if (sameSemanticIndex >= 0) {
            val current =
                existing[sameSemanticIndex]

            if (current.userLocked && !incoming.userLocked) {
                return existing
            }

            if (
                !incoming.userLocked &&
                incoming.confidence < current.confidence
            ) {
                return existing
            }

            return existing.toMutableList().apply {
                this[sameSemanticIndex] = incoming
            }
        }

        return existing + incoming
    }
}
