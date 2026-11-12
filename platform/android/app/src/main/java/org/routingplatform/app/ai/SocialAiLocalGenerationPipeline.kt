package org.routingplatform.app.ai

data class SocialAiGenerationEnvelope(
    val input: SocialAiRuntimeInput,
    val userText: String,
    val recalledKnowledge: List<SocialAiKnowledge>,
    val contextKey: String? = null,
    val maximumRecallEntries: Int = 24,
    val maximumRecallValueCharacters: Int = 512,
    val maximumOutputTokens: Int = 192,
)

class SocialAiLocalGenerationPipeline(
    private val backend: LocalSocialAiTextGenerationBackend?,
) {
    fun generateOrNull(envelope: SocialAiGenerationEnvelope): SocialAiTextGenerationResult? {
        val selected = backend ?: return null
        val sanitized = SocialAiRecallSanitizer.sanitize(
            knowledge = envelope.recalledKnowledge,
            maximumEntries = envelope.maximumRecallEntries,
            maximumValueCharacters = envelope.maximumRecallValueCharacters,
        )
        val result = SocialAiTextGenerationCoordinator(selected).generateOrNull(
            input = envelope.input,
            userText = envelope.userText,
            rememberedContext = sanitized,
            maximumOutputTokens = envelope.maximumOutputTokens,
        ) ?: return null

        require(result.backendId == selected.backendId)
        return SocialAiGeneratedTextEnforcer.enforce(envelope.input, result)
    }
}
