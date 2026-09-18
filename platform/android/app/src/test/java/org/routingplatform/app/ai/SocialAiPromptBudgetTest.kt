package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiPromptBudgetTest {
    @Test
    fun truncatesUserAndMemoryBeforeNativeInference() {
        val request = request(
            userText = "u".repeat(100),
            memory = listOf(knowledge("k1", "v".repeat(100))),
        )
        val admitted = SocialAiPromptAdmission.admit(
            request,
            SocialAiPromptBudget(
                maximumPromptCharacters = 80,
                maximumUserCharacters = 20,
                maximumMemoryCharacters = 10,
            ),
        )
        assertEquals(20, admitted.userText.length)
        assertTrue(admitted.rememberedContext.sumOf { it.key.length + it.value.length } <= 10)
        assertTrue(SocialAiPromptAdmission.promptCharacters(admitted) <= 80)
    }

    @Test
    fun rejectsWhenSystemInstructionAloneExceedsBudget() {
        assertThrows(IllegalArgumentException::class.java) {
            SocialAiPromptAdmission.admit(
                request(userText = "x", systemInstruction = "s".repeat(100)),
                SocialAiPromptBudget(32, 16, 0),
            )
        }
    }

    private fun request(
        userText: String,
        systemInstruction: String = "policy",
        memory: List<SocialAiKnowledge> = emptyList(),
    ) = SocialAiTextGenerationRequest(
        systemInstruction = systemInstruction,
        userText = userText,
        responsePlan = SocialAiRuntime.plan(SocialAiRuntimeInput(SocialAiPersonalitySettings())),
        rememberedContext = memory,
    )

    private fun knowledge(key: String, value: String) = SocialAiKnowledge(
        id = key,
        kind = SocialAiKnowledgeKind.Preference,
        key = key,
        value = value,
        confidence = 1.0,
        scope = SocialAiMemoryScope.LongTerm,
        source = "test",
        observedAtEpochMillis = 1,
    )
}
