package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiMemoryCodecTest {

    @Test
    fun roundTripPreservesGeneralAndContextualKnowledge() {
        val knowledge =
            listOf(
                SocialAiKnowledge(
                    id = "fact.favorite-city",
                    kind = SocialAiKnowledgeKind.Fact,
                    key = "user.favorite.city",
                    value = "Muenchen",
                    confidence = 1.0,
                    scope = SocialAiMemoryScope.LongTerm,
                    source = "explicit-user",
                    observedAtEpochMillis = 1_000L,
                    userLocked = true,
                ),
                SocialAiKnowledge(
                    id = "pattern.night-energy",
                    kind = SocialAiKnowledgeKind.InteractionPattern,
                    key = "conversation.energy",
                    value = "quiet",
                    confidence = 0.8,
                    scope = SocialAiMemoryScope.Contextual,
                    contextKey = "drive:night",
                    source = "conversation",
                    observedAtEpochMillis = 2_000L,
                ),
            )

        val encoded =
            SocialAiMemoryCodec.encode(knowledge)

        val decoded =
            SocialAiMemoryCodec.decode(encoded)

        assertEquals(knowledge, decoded)
        assertTrue(decoded.first().userLocked)
        assertEquals("drive:night", decoded.last().contextKey)
    }

    @Test
    fun duplicateIdsAreRejectedBeforePersistence() {
        val entry =
            SocialAiKnowledge(
                id = "duplicate",
                kind = SocialAiKnowledgeKind.Preference,
                key = "social.humor",
                value = "dry",
                confidence = 1.0,
                scope = SocialAiMemoryScope.LongTerm,
                source = "explicit-user",
                observedAtEpochMillis = 1L,
            )

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            SocialAiMemoryCodec.encode(
                listOf(entry, entry)
            )
        }
    }
}
