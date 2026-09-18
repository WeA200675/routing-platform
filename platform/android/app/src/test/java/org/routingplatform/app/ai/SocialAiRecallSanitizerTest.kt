package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SocialAiRecallSanitizerTest {
    @Test
    fun stripsControlCharactersAndBoundsModelFacingValues() {
        val entry =
            SocialAiKnowledge(
                id = "1",
                kind = SocialAiKnowledgeKind.Fact,
                key = "name\u0000",
                value = "x".repeat(800) + "\u0007",
                confidence = 1.0,
                scope = SocialAiMemoryScope.LongTerm,
                source = "explicit\u0001",
                observedAtEpochMillis = 1,
                userLocked = true,
            )

        val sanitized =
            SocialAiRecallSanitizer.sanitize(
                listOf(entry),
                maximumValueCharacters = 128,
            ).single()

        assertEquals(128, sanitized.value.length)
        assertFalse(sanitized.key.contains('\u0000'))
        assertFalse(sanitized.source.contains('\u0001'))
    }
}
