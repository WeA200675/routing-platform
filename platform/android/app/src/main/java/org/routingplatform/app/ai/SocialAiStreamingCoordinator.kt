package org.routingplatform.app.ai

/**
 * Buffers backend token events until a final result has passed the deterministic
 * post-generation enforcement boundary. This deliberately trades speculative
 * token presentation for safety: unverified model text is never presented.
 */
class SocialAiStreamingCoordinator(
    private val backend: StreamingLocalSocialAiTextGenerationBackend?,
) {
    fun generateOrNull(
        input: SocialAiRuntimeInput,
        userText: String,
        rememberedContext: List<SocialAiKnowledge> = emptyList(),
        maximumOutputTokens: Int = 192,
        cancellation: SocialAiGenerationCancellation = SocialAiGenerationCancellation.Never,
        emit: (SocialAiGenerationEvent) -> Unit,
    ): SocialAiTextGenerationResult? {
        val selected = backend ?: return null
        if (cancellation.isCancelled()) {
            emit(SocialAiGenerationEvent.Cancelled)
            return null
        }

        val plan = SocialAiRuntime.plan(input)
        val request = SocialAiTextGenerationRequest(
            systemInstruction = SocialAiPromptPolicy.systemInstruction(input.context, plan),
            userText = userText,
            responsePlan = plan,
            rememberedContext = SocialAiRecallSanitizer.sanitize(rememberedContext),
            maximumOutputTokens = maximumOutputTokens,
        )

        var completed: SocialAiTextGenerationResult? = null
        var cancelled = false
        selected.generateStreaming(request, cancellation) { event ->
            when (event) {
                is SocialAiGenerationEvent.Token -> Unit // never expose unverified partial text
                is SocialAiGenerationEvent.Completed -> {
                    check(completed == null && !cancelled) { "Backend emitted multiple terminal events." }
                    completed = event.result
                }
                SocialAiGenerationEvent.Cancelled -> {
                    check(completed == null && !cancelled) { "Backend emitted multiple terminal events." }
                    cancelled = true
                }
            }
        }

        if (cancelled || cancellation.isCancelled()) {
            emit(SocialAiGenerationEvent.Cancelled)
            return null
        }
        val raw = checkNotNull(completed) { "Streaming backend ended without a terminal event." }
        require(raw.backendId == selected.backendId) { "Backend result identity mismatch." }
        val enforced = SocialAiGeneratedTextEnforcer.enforce(input, raw)
        emit(SocialAiGenerationEvent.Completed(enforced))
        return enforced
    }
}
