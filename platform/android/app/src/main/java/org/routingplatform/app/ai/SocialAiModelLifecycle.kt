package org.routingplatform.app.ai

import java.io.File

data class SocialAiRuntimeLimits(
    val maximumModelBytes: Long = 4L * 1024 * 1024 * 1024,
    val maximumContextTokens: Int = 4_096,
    val maximumOutputTokens: Int = 512,
    val minimumAvailableMemoryBytes: Long = 768L * 1024 * 1024,
) {
    init {
        require(maximumModelBytes > 0)
        require(maximumContextTokens in 256..32_768)
        require(maximumOutputTokens in 1..2_048)
        require(minimumAvailableMemoryBytes >= 0)
    }
}

data class SocialAiModelLoadRequest(
    val artifact: File,
    val metadata: LocalModelArtifactMetadata,
    val availableMemoryBytes: Long,
)

sealed interface SocialAiModelLoadResult {
    data class Loaded(val modelId: String) : SocialAiModelLoadResult
    data class Rejected(val reason: String) : SocialAiModelLoadResult
}

interface ManagedLocalSocialAiBackend : LocalSocialAiTextGenerationBackend {
    val isLoaded: Boolean
    fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult
    fun unload()
}

class SocialAiModelLifecycle(
    private val backend: ManagedLocalSocialAiBackend,
    private val limits: SocialAiRuntimeLimits = SocialAiRuntimeLimits(),
) {
    fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
        if (!request.artifact.isFile) {
            return SocialAiModelLoadResult.Rejected("Model artifact is not a regular file.")
        }
        if (request.artifact.length() > limits.maximumModelBytes) {
            return SocialAiModelLoadResult.Rejected("Model artifact exceeds the configured size limit.")
        }
        if (request.availableMemoryBytes < limits.minimumAvailableMemoryBytes) {
            return SocialAiModelLoadResult.Rejected("Insufficient available memory for local inference.")
        }
        if (request.metadata != backend.modelMetadata) {
            return SocialAiModelLoadResult.Rejected("Model metadata does not match the selected backend.")
        }
        if (!SocialAiModelIntegrity.verify(request.artifact, request.metadata)) {
            return SocialAiModelLoadResult.Rejected("Model SHA-256 verification failed.")
        }

        if (backend.isLoaded) backend.unload()
        return backend.load(request)
    }

    fun unload() = backend.unload()

    fun admittedOutputTokens(requested: Int): Int =
        requested.coerceIn(1, limits.maximumOutputTokens)
}
