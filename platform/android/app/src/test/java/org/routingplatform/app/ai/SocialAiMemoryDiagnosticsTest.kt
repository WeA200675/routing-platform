package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class SocialAiMemoryDiagnosticsTest {
    @Test
    fun diagnosticsDoNotExposeKnowledgeValues() {
        val knowledge =
            listOf(
                SocialAiKnowledge(
                    id = "1",
                    kind = SocialAiKnowledgeKind.Preference,
                    key = "coffee",
                    value = "secret-value",
                    confidence = 1.0,
                    scope = SocialAiMemoryScope.LongTerm,
                    source = "explicit-user",
                    observedAtEpochMillis = 1,
                    userLocked = true,
                ),
                SocialAiKnowledge(
                    id = "2",
                    kind = SocialAiKnowledgeKind.InteractionPattern,
                    key = "energy",
                    value = "quiet",
                    confidence = 0.9,
                    scope = SocialAiMemoryScope.Contextual,
                    contextKey = "drive:night",
                    source = "automatic",
                    observedAtEpochMillis = 2,
                ),
            )

        val result =
            SocialAiMemoryDiagnosticsCalculator.calculate(knowledge)

        assertEquals(2, result.totalEntries)
        assertEquals(1, result.userLockedEntries)
        assertEquals(1, result.contextualEntries)
        assertEquals(1, result.automaticEntries)
    }
}
