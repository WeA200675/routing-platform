package org.routingplatform.app.ai

fun interface SocialAiGenerationCancellation {
    fun isCancelled(): Boolean

    companion object {
        val Never = SocialAiGenerationCancellation { false }
    }
}

sealed interface SocialAiGenerationEvent {
    data class Token(val text: String) : SocialAiGenerationEvent
    data class Completed(val result: SocialAiTextGenerationResult) : SocialAiGenerationEvent
    data object Cancelled : SocialAiGenerationEvent
}

interface StreamingLocalSocialAiTextGenerationBackend :
    LocalSocialAiTextGenerationBackend {
    fun generateStreaming(
        request: SocialAiTextGenerationRequest,
        cancellation: SocialAiGenerationCancellation = SocialAiGenerationCancellation.Never,
        emit: (SocialAiGenerationEvent) -> Unit,
    )
}

object SocialAiGeneratedTextEnforcer {
    fun enforce(
        input: SocialAiRuntimeInput,
        result: SocialAiTextGenerationResult,
    ): SocialAiTextGenerationResult {
        require(result.localInference) {
            "Social AI generation must remain local-only."
        }
        val text = result.text.trim()
        require(text.isNotEmpty()) {
            "Generated text must not be empty after normalization."
        }
        if (input.context == SocialAiResponseContext.CriticalGuidance) {
            require(text.length <= 1_024) {
                "Critical guidance output must remain concise."
            }
        }
        return result.copy(text = text)
    }
}
