package org.routingplatform.app.ai

/** JNI adapter for the pinned, on-device llama.cpp runtime. */
class JniSocialAiNativeEngine(
    private val nativeLibraryName: String = "social_ai_llama",
) : SocialAiNativeEngine {
    init {
        require(nativeLibraryName == "social_ai_llama")
        System.loadLibrary(nativeLibraryName)
    }

    override val engineId: String get() = nativeEngineId()
    override val artifactSha256: String get() = nativeArtifactSha256()
    override fun loadModel(localPath: String, contextTokens: Int): Boolean {
        require(localPath.isNotBlank())
        require(contextTokens in 512..4096)
        return nativeLoadModel(localPath, contextTokens)
    }
    override fun unloadModel() = nativeUnloadModel()
    override fun generate(prompt: String, maximumOutputTokens: Int): String {
        require(prompt.isNotBlank() && prompt.length <= 32_768)
        require(maximumOutputTokens in 1..512)
        return nativeGenerate(prompt, maximumOutputTokens)
    }

    private external fun nativeEngineId(): String
    private external fun nativeArtifactSha256(): String
    private external fun nativeLoadModel(localPath: String, contextTokens: Int): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(prompt: String, maximumOutputTokens: Int): String
}
