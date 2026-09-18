package org.routingplatform.app.ai

/**
 * Conservative character-based admission before native tokenization. This is
 * intentionally deterministic and independent of any model/runtime tokenizer.
 */
data class SocialAiPromptBudget(
    val maximumPromptCharacters: Int = 12_288,
    val maximumUserCharacters: Int = 8_192,
    val maximumMemoryCharacters: Int = 4_096,
) {
    init {
        require(maximumPromptCharacters in 1..32_768)
        require(maximumUserCharacters in 1..16_384)
        require(maximumMemoryCharacters in 0..16_384)
        require(maximumUserCharacters <= maximumPromptCharacters)
    }
}

object SocialAiPromptAdmission {
    fun admit(
        request: SocialAiTextGenerationRequest,
        budget: SocialAiPromptBudget = SocialAiPromptBudget(),
    ): SocialAiTextGenerationRequest {
        val user = request.userText.take(budget.maximumUserCharacters)
        var remainingMemory = budget.maximumMemoryCharacters
        val memory = request.rememberedContext.mapNotNull { item ->
            if (remainingMemory <= 0) return@mapNotNull null
            val key = item.key.take(160)
            val availableValue = (remainingMemory - key.length).coerceAtLeast(0)
            if (availableValue == 0) return@mapNotNull null
            val value = item.value.take(availableValue)
            remainingMemory -= key.length + value.length
            item.copy(key = key, value = value)
        }
        val admitted = request.copy(userText = user, rememberedContext = memory)
        val characters = promptCharacters(admitted)
        require(characters <= budget.maximumPromptCharacters) {
            "Prompt exceeds deterministic local inference budget."
        }
        return admitted
    }

    fun promptCharacters(request: SocialAiTextGenerationRequest): Int =
        request.systemInstruction.length + request.userText.length +
            request.rememberedContext.sumOf { it.key.length + it.value.length }
}
