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
    private val leases: SocialAiModelLeaseRegistry = SocialAiModelLeaseRegistry(),
) {
    @Volatile
    var state: SocialAiRuntimeState = SocialAiRuntimeState.Unloaded
        private set

    @Volatile
    var modelLease: SocialAiModelLease? = null
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
            revokeLease()
            circuitBreaker.recordFailure()
            state = SocialAiRuntimeState.Failed("Local runtime load failed.")
            return SocialAiModelLoadResult.Rejected("Local runtime load failed.")
        }
        state = when (result) {
            is SocialAiModelLoadResult.Loaded -> {
                circuitBreaker.recordSuccess()
                modelLease = leases.activate(request.metadata)
                SocialAiRuntimeState.Ready(result.modelId)
            }
            is SocialAiModelLoadResult.Rejected -> {
                revokeLease()
                SocialAiRuntimeState.Failed(result.reason)
            }
        }
        return result
    }

    @Synchronized
    fun unload() = unload(resetCircuitBreaker = true)

    private fun unload(resetCircuitBreaker: Boolean) {
        lifecycle.unload()
        revokeLease()
        if (resetCircuitBreaker) circuitBreaker.reset()
        state = SocialAiRuntimeState.Unloaded
    }

    private fun revokeLease() {
        leases.revoke()
        modelLease = null
    }

    @Synchronized
    fun onResourcePressure(resources: SocialAiRuntimeResources): SocialAiRuntimePressureAction {
        val action = SocialAiRuntimePressurePolicy.evaluate(resources)
        if (action is SocialAiRuntimePressureAction.Unload && state is SocialAiRuntimeState.Ready) {
            lifecycle.unload()
            revokeLease()
            state = SocialAiRuntimeState.Unloaded
        }
        return action
    }

    @Synchronized
    fun generate(
        request: SocialAiTextGenerationRequest,
        resources: SocialAiRuntimeResources = SocialAiRuntimeResources(Long.MAX_VALUE),
    ): SocialAiTextGenerationResult {
        check(state is SocialAiRuntimeState.Ready) { "Local runtime is not ready." }
        val lease = checkNotNull(modelLease) { "Local runtime has no verified model lease." }
        leases.requireActive(lease)
        check(lease.modelId == backend.modelMetadata.modelId &&
            lease.revision == backend.modelMetadata.revision &&
            lease.sha256 == backend.modelMetadata.sha256.lowercase()) {
            "Loaded model provenance no longer matches backend metadata."
        }
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
            backend.generate(admitted).also { result ->
                check(result.localInference) { "Runtime result must report local inference." }
                check(result.backendId == backend.backendId) { "Runtime backend identity mismatch." }
                leases.requireActive(lease)
                circuitBreaker.recordSuccess()
            }
        } catch (error: RuntimeException) {
            backend.unload()
            revokeLease()
            circuitBreaker.recordFailure()
            state = SocialAiRuntimeState.Failed("Local runtime generation failed.")
            throw IllegalStateException("Local runtime generation failed closed.", error)
        }
    }
}
