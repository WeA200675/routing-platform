package org.routingplatform.app.ai

/**
 * Optional local text generation. The deterministic plan is always computed
 * first and remains the safety authority for social expression.
 */
class SocialAiTextGenerationCoordinator(
    private val backend: LocalSocialAiTextGenerationBackend?,
) {
    fun generateOrNull(
        input: SocialAiRuntimeInput,
        userText: String,
        rememberedContext: List<SocialAiKnowledge> = emptyList(),
        maximumOutputTokens: Int = 192,
    ): SocialAiTextGenerationResult? {
        val selectedBackend = backend ?: return null
        val plan = SocialAiRuntime.plan(input)

        require(plan.flirtLevel == 0 || input.settings.adultFlirtOptIn) {
            "Effective flirt requires explicit adult opt-in."
        }
        if (input.context != SocialAiResponseContext.Conversation) {
            require(!plan.allowFlirt && plan.flirtLevel == 0) {
                "Flirt is forbidden outside conversation context."
            }
        }
        if (input.context == SocialAiResponseContext.CriticalGuidance) {
            require(plan.suppressNonessentialSocial) {
                "Critical guidance must suppress nonessential social behavior."
            }
        }

        return selectedBackend.generate(
            SocialAiTextGenerationRequest(
                systemInstruction =
                    SocialAiPromptPolicy.systemInstruction(
                        context = input.context,
                        plan = plan,
                    ),
                userText = userText,
                responsePlan = plan,
                rememberedContext = rememberedContext,
                maximumOutputTokens = maximumOutputTokens,
            )
        ).also { result ->
            require(result.localInference) {
                "G6.19 text generation backend must report local inference."
            }
            require(result.backendId == selectedBackend.backendId) {
                "Backend result identity mismatch."
            }
        }
    }
}
