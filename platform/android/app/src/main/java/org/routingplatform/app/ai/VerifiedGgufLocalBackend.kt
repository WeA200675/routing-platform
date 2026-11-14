package org.routingplatform.app.ai

interface SocialAiNativeEngine {
    val engineId: String
    val artifactSha256: String
    fun loadModel(localPath: String, contextTokens: Int): Boolean
    fun unloadModel()
    fun generate(prompt: String, maximumOutputTokens: Int): String
}

class VerifiedGgufLocalBackend(
    override val backendId: String,
    override val runtimeMetadata: OpenSourceComponentMetadata,
    override val modelMetadata: LocalModelArtifactMetadata,
    private val engine: SocialAiNativeEngine,
    private val contextTokens: Int = 4_096,
) : LocalSocialAiTextGenerationBackend {
    @Volatile private var loaded = false

    init {
        require(backendId.isNotBlank())
        require(contextTokens in 512..4096)
    }

    fun load(model: java.io.File): Boolean {
        check(!loaded) { "Local model is already loaded." }
        if (!model.isFile || !runCatching { SocialAiModelIntegrity.verify(model, modelMetadata) }.getOrDefault(false)) return false
        val success = runCatching { engine.loadModel(model.absolutePath, contextTokens) }.getOrDefault(false)
        loaded = success
        return success
    }

    fun close() {
        if (loaded) runCatching { engine.unloadModel() }
        loaded = false
    }

    override fun generate(request: SocialAiTextGenerationRequest): SocialAiTextGenerationResult {
        check(loaded) { "Local model is not loaded." }
        val boundedMemory = request.rememberedContext.take(32)
        val prompt = buildString {
            append(request.systemInstruction.take(8_192))
            append("\n\nUSER:\n")
            append(request.userText.take(16_384))
            if (boundedMemory.isNotEmpty()) {
                append("\n\nBOUNDED MEMORY:\n")
                boundedMemory.forEach { item ->
                    append("- ")
                    append(item.key.take(160))
                    append(": ")
                    append(item.value.take(512))
                    append('\n')
                }
            }
            append("\nASSISTANT:\n")
        }
        val result = engine.generate(prompt, request.maximumOutputTokens.coerceIn(1, 512)).trim()
        require(result.isNotBlank()) { "Local model returned no text." }
        return SocialAiTextGenerationResult(result.take(32_768), backendId, localInference = true)
    }
}
