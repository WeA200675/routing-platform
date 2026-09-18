package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.AiPreferences

class SocialAiLearningPolicyTest {

    @Test
    fun explicitUserMemoryIsStoredAndLockedEvenWhenAutomaticLearningIsOff() {
        val decision =
            SocialAiLearningPolicy.evaluate(
                preferences =
                    AiPreferences(
                        learningEnabled = false
                    ),
                candidate =
                    candidate(
                        origin =
                            SocialAiLearningOrigin.ExplicitUser,
                        confidence = 1.0,
                    ),
            )

        assertTrue(decision.shouldStore)
        assertEquals(
            SocialAiLearningDecisionReason.StoreExplicitUserMemory,
            decision.reason,
        )
        assertNotNull(decision.knowledge)
        assertTrue(decision.knowledge!!.userLocked)
    }

    @Test
    fun automaticLearningRequiresOptInAndConfidence() {
        val disabled =
            SocialAiLearningPolicy.evaluate(
                preferences = AiPreferences(learningEnabled = false),
                candidate =
                    candidate(
                        origin =
                            SocialAiLearningOrigin.ConversationInference,
                        confidence = 0.95,
                    ),
            )

        assertFalse(disabled.shouldStore)
        assertEquals(
            SocialAiLearningDecisionReason.RejectLearningDisabled,
            disabled.reason,
        )
        assertNull(disabled.knowledge)

        val lowConfidence =
            SocialAiLearningPolicy.evaluate(
                preferences = AiPreferences(learningEnabled = true),
                candidate =
                    candidate(
                        origin =
                            SocialAiLearningOrigin.ConversationInference,
                        confidence = 0.50,
                    ),
            )

        assertFalse(lowConfidence.shouldStore)
        assertEquals(
            SocialAiLearningDecisionReason.RejectLowConfidence,
            lowConfidence.reason,
        )

        val accepted =
            SocialAiLearningPolicy.evaluate(
                preferences = AiPreferences(learningEnabled = true),
                candidate =
                    candidate(
                        origin =
                            SocialAiLearningOrigin.ConversationInference,
                        confidence = 0.90,
                    ),
            )

        assertTrue(accepted.shouldStore)
        assertFalse(accepted.knowledge!!.userLocked)
    }

    @Test
    fun automaticLearningCannotReplaceUserLockedSemanticMemory() {
        val locked =
            SocialAiKnowledge(
                id = "explicit-humor",
                kind = SocialAiKnowledgeKind.SocialPreference,
                key = "social.humor.style",
                value = "dry",
                confidence = 1.0,
                scope = SocialAiMemoryScope.LongTerm,
                source = "explicit-user",
                observedAtEpochMillis = 1L,
                userLocked = true,
            )

        val automatic =
            locked.copy(
                id = "inferred-humor",
                value = "slapstick",
                confidence = 1.0,
                source = "conversation",
                observedAtEpochMillis = 2L,
                userLocked = false,
            )

        val merged =
            SocialAiMemoryMergePolicy.merge(
                existing = listOf(locked),
                incoming = automatic,
            )

        assertEquals(listOf(locked), merged)
    }

    @Test
    fun explicitCorrectionCanReplaceLockedSemanticMemory() {
        val old =
            SocialAiKnowledge(
                id = "old",
                kind = SocialAiKnowledgeKind.SocialPreference,
                key = "social.humor.style",
                value = "dry",
                confidence = 1.0,
                scope = SocialAiMemoryScope.LongTerm,
                source = "explicit-user",
                observedAtEpochMillis = 1L,
                userLocked = true,
            )

        val correction =
            old.copy(
                id = "correction",
                value = "playful",
                source = "user-correction",
                observedAtEpochMillis = 2L,
                userLocked = true,
            )

        val merged =
            SocialAiMemoryMergePolicy.merge(
                existing = listOf(old),
                incoming = correction,
            )

        assertEquals(1, merged.size)
        assertEquals("playful", merged.single().value)
        assertTrue(merged.single().userLocked)
    }

    private fun candidate(
        origin: SocialAiLearningOrigin,
        confidence: Double,
    ): SocialAiLearningCandidate =
        SocialAiLearningCandidate(
            id = "candidate-1",
            kind = SocialAiKnowledgeKind.SocialPreference,
            key = "social.humor.style",
            value = "dry",
            confidence = confidence,
            scope = SocialAiMemoryScope.LongTerm,
            source = "test",
            observedAtEpochMillis = 1L,
            origin = origin,
        )
}
