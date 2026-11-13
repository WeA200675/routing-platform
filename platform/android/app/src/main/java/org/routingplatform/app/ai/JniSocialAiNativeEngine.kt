package org.routingplatform.app.ai

class JniSocialAiNativeEngine(
    private val nativeLibraryName: String = "social_ai_llama",
) : SocialAiNativeEngine,
    SocialAiRuntimeBuildIdentified,
    SocialAiRuntimeArtifactIdentified {

    init {
        require(nativeLibraryName == "social_ai_llama")
        System.loadLibrary(nativeLibraryName)
    }

    override val engineId: String
        get() = nativeEngineId()

    override val artifactSha256: String
        get() = nativeArtifactSha256()

    override val runtimeBuildId: String
        get() = engineId

    override val runtimeArtifactSha256: String
        get() = artifactSha256

    override fun loadModel(localPath: String, contextTokens: Int): Boolean =
        nativeLoadModel(localPath, contextTokens)

    override fun unloadModel() {
        nativeUnloadModel()
    }

    override fun generate(prompt: String, maximumOutputTokens: Int): String =
        nativeGenerate(prompt, maximumOutputTokens)

    private external fun nativeEngineId(): String
    private external fun nativeArtifactSha256(): String
    private external fun nativeLoadModel(
        localPath: String,
        contextTokens: Int,
    ): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(
        prompt: String,
        maximumOutputTokens: Int,
    ): String
}
