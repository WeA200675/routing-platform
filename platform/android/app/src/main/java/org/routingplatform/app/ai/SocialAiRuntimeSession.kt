package org.routingplatform.app.ai

sealed interface SocialAiRuntimeState {
    data object Unloaded : SocialAiRuntimeState
    data class Ready(val modelId: String) : SocialAiRuntimeState
    data class Failed(val reason: String) : SocialAiRuntimeState
}

/** Serializes model lifecycle transitions and fails closed after runtime errors. */
class SocialAiRuntimeSession(
    private val backend: ManagedLocalSocialAiBackend,
    private val lifecycle: SocialAiModelLifecycle,
) {
    @Volatile
    var state: SocialAiRuntimeState = SocialAiRuntimeState.Unloaded
        private set

    @Synchronized
    fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
        if (state is SocialAiRuntimeState.Ready) unload()
        val result = try {
            lifecycle.load(request)
        } catch (error: RuntimeException) {
            backend.unload()
            state = SocialAiRuntimeState.Failed("Local runtime load failed.")
            return SocialAiModelLoadResult.Rejected("Local runtime load failed.")
        }
        state = when (result) {
            is SocialAiModelLoadResult.Loaded -> SocialAiRuntimeState.Ready(result.modelId)
            is SocialAiModelLoadResult.Rejected -> SocialAiRuntimeState.Failed(result.reason)
        }
        return result
    }

    @Synchronized
    fun unload() {
        lifecycle.unload()
        state = SocialAiRuntimeState.Unloaded
    }

    @Synchronized
    fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
        check(state is SocialAiRuntimeState.Ready) { "Local runtime is not ready." }
        return try {
            backend.generate(SocialAiPromptAdmission.admit(request))
        } catch (error: RuntimeException) {
            backend.unload()
            state = SocialAiRuntimeState.Failed("Local runtime generation failed.")
            throw IllegalStateException("Local runtime generation failed closed.", error)
        }
    }
}
