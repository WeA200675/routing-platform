package org.routingplatform.app.ai

interface SocialAiMemoryPersistence {
    fun load(
        storeId: String,
    ): List<SocialAiKnowledge>

    fun save(
        storeId: String,
        knowledge: List<SocialAiKnowledge>,
    ): Boolean

    fun clear(
        storeId: String,
    ): Boolean

    fun exists(
        storeId: String,
    ): Boolean
}

class SocialAiLearningRepository(
    private val persistence: SocialAiMemoryPersistence,
) {

    fun consider(
        storeId: String,
        preferences: org.routingplatform.app.profile.AiPreferences,
        candidate: SocialAiLearningCandidate,
        automaticConfidenceThreshold: Double =
            SocialAiLearningPolicy.DEFAULT_AUTOMATIC_CONFIDENCE_THRESHOLD,
    ): SocialAiLearningDecision {
        val decision =
            SocialAiLearningPolicy.evaluate(
                preferences = preferences,
                candidate = candidate,
                automaticConfidenceThreshold =
                    automaticConfidenceThreshold,
            )

        val accepted =
            decision.knowledge
                ?: return decision

        val current =
            persistence.load(storeId)

        val merged =
            SocialAiMemoryMergePolicy.merge(
                existing = current,
                incoming = accepted,
            )

        if (merged == current) {
            return decision
        }

        check(
            persistence.save(
                storeId = storeId,
                knowledge = merged,
            )
        ) {
            "Social AI memory persistence failed."
        }

        return decision
    }

    fun rememberExplicitly(
        storeId: String,
        id: String,
        kind: SocialAiKnowledgeKind,
        key: String,
        value: String,
        scope: SocialAiMemoryScope =
            SocialAiMemoryScope.LongTerm,
        contextKey: String? = null,
        source: String = "explicit-user",
        observedAtEpochMillis: Long,
        correction: Boolean = false,
    ): SocialAiLearningDecision =
        consider(
            storeId = storeId,
            preferences =
                org.routingplatform.app.profile.AiPreferences(),
            candidate =
                SocialAiLearningCandidate(
                    id = id,
                    kind = kind,
                    key = key,
                    value = value,
                    confidence = 1.0,
                    scope = scope,
                    contextKey = contextKey,
                    source = source,
                    observedAtEpochMillis =
                        observedAtEpochMillis,
                    origin =
                        if (correction) {
                            SocialAiLearningOrigin.UserCorrection
                        } else {
                            SocialAiLearningOrigin.ExplicitUser
                        },
                ),
        )

    fun recall(
        storeId: String,
        contextKey: String? = null,
        maximumEntries: Int = 24,
    ): List<SocialAiKnowledge> =
        SocialAiMemorySelector.select(
            knowledge = persistence.load(storeId),
            contextKey = contextKey,
            maximumEntries = maximumEntries,
        )

    fun forget(
        storeId: String,
        id: String,
    ): Boolean {
        val current =
            persistence.load(storeId)

        val updated =
            current.filterNot {
                it.id == id
            }

        if (updated.size == current.size) {
            return false
        }

        check(
            persistence.save(
                storeId = storeId,
                knowledge = updated,
            )
        ) {
            "Social AI memory persistence failed."
        }

        return true
    }

    fun forgetAll(
        storeId: String,
    ): Boolean =
        persistence.clear(storeId)
}

object SocialAiMemorySelector {

    fun select(
        knowledge: List<SocialAiKnowledge>,
        contextKey: String? = null,
        maximumEntries: Int = 24,
    ): List<SocialAiKnowledge> {
        require(maximumEntries in 1..64) {
            "maximumEntries must be in [1, 64]."
        }

        return knowledge
            .asSequence()
            .filter { entry ->
                when (entry.scope) {
                    SocialAiMemoryScope.ShortTerm,
                    SocialAiMemoryScope.LongTerm ->
                        true

                    SocialAiMemoryScope.Contextual ->
                        contextKey != null &&
                            entry.contextKey == contextKey
                }
            }
            .sortedWith(
                compareByDescending<SocialAiKnowledge> {
                    it.userLocked
                }
                    .thenByDescending {
                        it.confidence
                    }
                    .thenByDescending {
                        it.observedAtEpochMillis
                    }
            )
            .distinctBy {
                listOf(
                    it.kind.name,
                    it.key,
                    it.scope.name,
                    it.contextKey.orEmpty(),
                ).joinToString("|")
            }
            .take(maximumEntries)
            .toList()
    }
}
