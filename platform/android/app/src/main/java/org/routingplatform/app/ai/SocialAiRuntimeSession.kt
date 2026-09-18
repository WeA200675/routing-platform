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
    private val circuitBreaker: SocialAiRuntimeCircuitBreaker = SocialAiRuntimeCircuitBreaker(),
) {
    @Volatile
    var state: SocialAiRuntimeState = SocialAiRuntimeState.Unloaded
        private set

    @Synchronized
    fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
        if (!circuitBreaker.allowAttempt()) {
            state = SocialAiRuntimeState.Failed("Local runtime circuit breaker is open.")
            return SocialAiModelLoadResult.Rejected("Local runtime circuit breaker is open.")
        }
        if (state is SocialAiRuntimeState.Ready) unload(resetCircuitBreaker = false)
        val result = try {
            lifecycle.load(request)
        } catch (error: RuntimeException) {
            backend.unload()
            circuitBreaker.recordFailure()
            state = SocialAiRuntimeState.Failed("Local runtime load failed.")
            return SocialAiModelLoadResult.Rejected("Local runtime load failed.")
        }
        state = when (result) {
            is SocialAiModelLoadResult.Loaded -> {
                circuitBreaker.recordSuccess()
                SocialAiRuntimeState.Ready(result.modelId)
            }
            is SocialAiModelLoadResult.Rejected -> SocialAiRuntimeState.Failed(result.reason)
        }
        return result
    }

    @Synchronized
    fun unload() = unload(resetCircuitBreaker = true)

    private fun unload(resetCircuitBreaker: Boolean) {
        lifecycle.unload()
        if (resetCircuitBreaker) circuitBreaker.reset()
        state = SocialAiRuntimeState.Unloaded
    }

    @Synchronized
    fun generate(
        request: SocialAiTextGenerationRequest,
        resources: SocialAiRuntimeResources = SocialAiRuntimeResources(Long.MAX_VALUE),
    ): SocialAiTextGenerationResult {
        check(state is SocialAiRuntimeState.Ready) { "Local runtime is not ready." }
        check(circuitBreaker.allowAttempt()) { "Local runtime circuit breaker is open." }
        val admission = SocialAiRuntimeResourceGovernor.admit(
            requestedOutputTokens = request.maximumOutputTokens,
            resources = resources,
        )
        val allowed = admission as? SocialAiRuntimeAdmission.Allowed
            ?: throw IllegalStateException((admission as SocialAiRuntimeAdmission.Rejected).reason)
        val admitted = SocialAiPromptAdmission.admit(
            request.copy(maximumOutputTokens = allowed.maximumOutputTokens)
        )
        return try {
            backend.generate(admitted).also { circuitBreaker.recordSuccess() }
        } catch (error: RuntimeException) {
            backend.unload()
            circuitBreaker.recordFailure()
            state = SocialAiRuntimeState.Failed("Local runtime generation failed.")
            throw IllegalStateException("Local runtime generation failed closed.", error)
        }
    }
}
