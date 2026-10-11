package org.routingplatform.app.ai

/**
 * JNI-facing contract. The implementation deliberately contains no networking
 * API and receives only a verified local model path.
 */
interface SocialAiNativeEngine {
    /** Immutable identifier of the reviewed native runtime build. */
    val engineId: String
    /** SHA-256 of the exact reviewed native runtime artifact loaded in this process. */
    val artifactSha256: String
    fun loadModel(localPath: String, contextTokens: Int): Boolean
    fun unloadModel()
    fun generate(prompt: String, maximumOutputTokens: Int): String
}

/** Exposes the immutable native build identity at the Kotlin admission boundary. */
interface SocialAiRuntimeBuildIdentified {
    val runtimeBuildId: String
}

/** Binds the active native runtime binary to reviewed distribution evidence. */
interface SocialAiRuntimeArtifactIdentified {
    val runtimeArtifactSha256: String
}

class VerifiedGgufLocalBackend(
    override val backendId: String,
    override val runtimeMetadata: OpenSourceComponentMetadata,
    override val modelMetadata: LocalModelArtifactMetadata,
    private val engine: SocialAiNativeEngine,
    private val contextTokens: Int = 4_096,
) : ManagedLocalSocialAiBackend, SocialAiRuntimeBuildIdentified, SocialAiRuntimeArtifactIdentified {
    override val runtimeBuildId: String
        get() = engine.engineId

    override val runtimeArtifactSha256: String
        get() = engine.artifactSha256.lowercase()

    @Volatile
    override var isLoaded: Boolean = false
        private set

    override fun load(request: SocialAiModelLoadRequest): SocialAiModelLoadResult {
        require(request.metadata == modelMetadata)
        check(!isLoaded) { "A model is already loaded." }
        val loaded = engine.loadModel(request.artifact.absolutePath, contextTokens)
        isLoaded = loaded
        return if (loaded) {
            SocialAiModelLoadResult.Loaded(modelMetadata.modelId)
        } else {
            SocialAiModelLoadResult.Rejected("Native local runtime rejected the model.")
        }
    }

    override fun unload() {
        if (isLoaded) engine.unloadModel()
        isLoaded = false
    }

    override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
        check(isLoaded) { "Local model must be loaded before generation." }
        val prompt = buildString {
            append(request.systemInstruction)
            append("\n\nUSER:\n")
            append(request.userText)
            if (request.rememberedContext.isNotEmpty()) {
                append("\n\nBOUNDED MEMORY:\n")
                request.rememberedContext.forEach { item ->
                    append("- ")
                    append(item.key)
                    append(": ")
                    append(item.value)
                    append('\n')
                }
            }
            append("\nASSISTANT:\n")
        }
        val text = engine.generate(prompt, request.maximumOutputTokens).trim()
        return SocialAiTextGenerationResult(text, backendId, localInference = true)
    }
}
