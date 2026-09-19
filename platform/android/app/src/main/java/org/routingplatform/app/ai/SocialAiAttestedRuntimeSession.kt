package org.routingplatform.app.ai

/**
 * Production-facing admission boundary for a local model session.
 *
 * Attestation is checked before model bytes reach the native runtime. The
 * wrapper intentionally exposes no network or routing-authority dependency.
 */
class SocialAiAttestedRuntimeSession(
    private val backend: ManagedLocalSocialAiBackend,
    lifecycle: SocialAiModelLifecycle,
    private val attestation: SocialAiRuntimeAttestation,
    private val deviceAbi: String,
) {
    private val session = SocialAiRuntimeSession(backend, lifecycle)

    val state: SocialAiRuntimeState
        get() = session.state

    val modelLease: SocialAiModelLease?
        get() = session.modelLease

    @Synchronized
    fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
        return try {
            SocialAiRuntimeAttestationGate.requireAdmitted(
                attestation = attestation,
                backend = backend,
                deviceAbi = deviceAbi,
            )
            require(request.metadata == attestation.provenance.model) {
                "Load request model metadata is not covered by the runtime attestation."
            }
            session.load(request)
        } catch (error: IllegalArgumentException) {
            SocialAiModelLoadResult.Rejected(
                error.message ?: "Runtime attestation rejected local model load."
            )
        }
    }

    @Synchronized
    fun unload() = session.unload()

    @Synchronized
    fun generate(
        request: SocialAiTextGenerationRequest,
        resources: SocialAiRuntimeResources = SocialAiRuntimeResources(Long.MAX_VALUE),
    ): SocialAiTextGenerationResult {
        SocialAiRuntimeAttestationGate.requireAdmitted(
            attestation = attestation,
            backend = backend,
            deviceAbi = deviceAbi,
        )
        val lease = checkNotNull(session.modelLease) {
            "Attested local runtime has no active model lease."
        }
        check(lease.modelId == attestation.provenance.model.modelId &&
            lease.revision == attestation.provenance.model.revision &&
            lease.sha256 == attestation.provenance.model.sha256.lowercase()) {
            "Active model lease no longer matches the attested model."
        }
        return session.generate(request, resources)
    }
}
