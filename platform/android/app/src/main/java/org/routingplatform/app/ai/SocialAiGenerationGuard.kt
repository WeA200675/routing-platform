package org.routingplatform.app.ai

import java.util.concurrent.atomic.AtomicBoolean

sealed interface SocialAiGenerationResult {
    data class Generated(val text: String) : SocialAiGenerationResult
    data class Rejected(val reason: String) : SocialAiGenerationResult
}

/** Single-flight production guard around the native engine. */
class SocialAiGenerationGuard(
    private val engine: SocialAiNativeEngine,
    private val maximumPromptCharacters: Int = 8_192,
    private val maximumOutputTokens: Int = 256,
) {
    private val inFlight = AtomicBoolean(false)

    fun generate(prompt: String, requestedOutputTokens: Int): SocialAiGenerationResult {
        val clean = prompt.trim()
        if (clean.isEmpty() || clean.length > maximumPromptCharacters) {
            return SocialAiGenerationResult.Rejected("Prompt is outside the admitted size boundary.")
        }
        if (!inFlight.compareAndSet(false, true)) {
            return SocialAiGenerationResult.Rejected("Local inference is already running.")
        }
        return try {
            val text = engine.generate(clean, requestedOutputTokens.coerceIn(1, maximumOutputTokens)).trim()
            if (text.isEmpty()) SocialAiGenerationResult.Rejected("Local inference returned no text.")
            else SocialAiGenerationResult.Generated(text)
        } catch (error: RuntimeException) {
            SocialAiGenerationResult.Rejected(error.message ?: "Local inference failed.")
        } finally {
            inFlight.set(false)
        }
    }
}
