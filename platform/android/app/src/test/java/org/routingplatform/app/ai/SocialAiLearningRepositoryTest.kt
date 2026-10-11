package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.AiPreferences

class SocialAiLearningRepositoryTest {

    @Test
    fun repositoryPersistsAcceptedAutomaticLearning() {
        val persistence = InMemoryPersistence()
        val repository = SocialAiLearningRepository(persistence)

        val decision =
            repository.consider(
                storeId = "profile-memory",
                preferences = AiPreferences(learningEnabled = true),
                candidate = candidate(
                    id = "auto-1",
                    confidence = 0.9,
                    origin = SocialAiLearningOrigin.ConversationInference,
                ),
            )

        assertTrue(decision.shouldStore)
        assertEquals(1, persistence.load("profile-memory").size)
        assertFalse(persistence.load("profile-memory").single().userLocked)
    }

    @Test
    fun repositoryDoesNotPersistRejectedAutomaticLearning() {
        val persistence = InMemoryPersistence()
        val repository = SocialAiLearningRepository(persistence)

        val decision =
            repository.consider(
                storeId = "profile-memory",
                preferences = AiPreferences(learningEnabled = false),
                candidate = candidate(
                    id = "auto-1",
                    confidence = 0.9,
                    origin = SocialAiLearningOrigin.ConversationInference,
                ),
            )

        assertFalse(decision.shouldStore)
        assertTrue(persistence.load("profile-memory").isEmpty())
    }

    @Test
    fun recallPrefersUserLockedHighConfidenceAndMatchingContext() {
        val persistence = InMemoryPersistence()
        val repository = SocialAiLearningRepository(persistence)

        persistence.save(
            "profile-memory",
            listOf(
                knowledge(
                    id = "global-low",
                    key = "social.humor.style",
                    value = "playful",
                    confidence = 0.5,
                    userLocked = false,
                    timestamp = 3L,
                ),
                knowledge(
                    id = "global-explicit",
                    key = "social.humor.style",
                    value = "dry",
                    confidence = 1.0,
                    userLocked = true,
                    timestamp = 1L,
                ),
                knowledge(
                    id = "night",
                    key = "conversation.energy",
                    value = "quiet",
                    confidence = 0.9,
                    userLocked = false,
                    timestamp = 2L,
                    scope = SocialAiMemoryScope.Contextual,
                    contextKey = "drive:night",
                ),
                knowledge(
                    id = "day",
                    key = "conversation.energy",
                    value = "chatty",
                    confidence = 0.95,
                    userLocked = false,
                    timestamp = 4L,
                    scope = SocialAiMemoryScope.Contextual,
                    contextKey = "drive:day",
                ),
            ),
        )

        val recalled =
            repository.recall(
                storeId = "profile-memory",
                contextKey = "drive:night",
            )

        assertEquals(2, recalled.size)
        assertEquals("global-explicit", recalled[0].id)
        assertEquals("night", recalled[1].id)
        assertTrue(recalled.none { it.id == "day" })
    }

    @Test
    fun explicitMemoryCanBeForgottenIndividuallyOrCompletely() {
        val persistence = InMemoryPersistence()
        val repository = SocialAiLearningRepository(persistence)

        repository.rememberExplicitly(
            storeId = "profile-memory",
            id = "remembered",
            kind = SocialAiKnowledgeKind.Fact,
            key = "user.favorite.city",
            value = "Muenchen",
            observedAtEpochMillis = 10L,
        )

        assertEquals(1, persistence.load("profile-memory").size)
        assertTrue(repository.forget("profile-memory", "remembered"))
        assertTrue(persistence.load("profile-memory").isEmpty())
        assertFalse(repository.forget("profile-memory", "missing"))

        repository.rememberExplicitly(
            storeId = "profile-memory",
            id = "remembered-again",
            kind = SocialAiKnowledgeKind.Preference,
            key = "user.route.tone",
            value = "calm",
            observedAtEpochMillis = 11L,
        )

        assertTrue(repository.forgetAll("profile-memory"))
        assertTrue(persistence.load("profile-memory").isEmpty())
    }

    private fun candidate(
        id: String,
        confidence: Double,
        origin: SocialAiLearningOrigin,
    ) =
        SocialAiLearningCandidate(
            id = id,
            kind = SocialAiKnowledgeKind.SocialPreference,
            key = "social.humor.style",
            value = "dry",
            confidence = confidence,
            scope = SocialAiMemoryScope.LongTerm,
            source = "test",
            observedAtEpochMillis = 1L,
            origin = origin,
        )

    private fun knowledge(
        id: String,
        key: String,
        value: String,
        confidence: Double,
        userLocked: Boolean,
        timestamp: Long,
        scope: SocialAiMemoryScope = SocialAiMemoryScope.LongTerm,
        contextKey: String? = null,
    ) =
        SocialAiKnowledge(
            id = id,
            kind =
                if (key.startsWith("social.")) {
                    SocialAiKnowledgeKind.SocialPreference
                } else {
                    SocialAiKnowledgeKind.InteractionPattern
                },
            key = key,
            value = value,
            confidence = confidence,
            scope = scope,
            contextKey = contextKey,
            source = "test",
            observedAtEpochMillis = timestamp,
            userLocked = userLocked,
        )

    private class InMemoryPersistence : SocialAiMemoryPersistence {
        private val stores =
            mutableMapOf<String, List<SocialAiKnowledge>>()

        override fun load(
            storeId: String,
        ): List<SocialAiKnowledge> =
            stores[storeId].orEmpty()

        override fun save(
            storeId: String,
            knowledge: List<SocialAiKnowledge>,
        ): Boolean {
            stores[storeId] = knowledge
            return true
        }

        override fun clear(
            storeId: String,
        ): Boolean {
            stores.remove(storeId)
            return true
        }

        override fun exists(
            storeId: String,
        ): Boolean =
            stores.containsKey(storeId)
    }
}
