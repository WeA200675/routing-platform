package org.routingplatform.app.ai

/**
 * Concrete JNI adapter for the separately built and reviewed local llama runtime.
 *
 * The library is loaded only when a release candidate intentionally packages it.
 * No network API is exposed across this boundary.
 */
class JniSocialAiNativeEngine(
    private val nativeLibraryName: String = "social_ai_llama",
) : SocialAiNativeEngine {
    init {
        require(nativeLibraryName == "social_ai_llama") { "Only the reviewed Social AI runtime library is allowed." }
        System.loadLibrary(nativeLibraryName)
    }

    override val engineId: String
        get() = nativeEngineId()

    override val artifactSha256: String
        get() = nativeArtifactSha256()

    override fun loadModel(localPath: String, contextTokens: Int): Boolean {
        require(localPath.isNotBlank())
        require(contextTokens > 0)
        return nativeLoadModel(localPath, contextTokens)
    }

    override fun unloadModel() {
        nativeUnloadModel()
    }

    override fun generate(prompt: String, maximumOutputTokens: Int): String {
        require(prompt.isNotBlank())
        require(maximumOutputTokens > 0)
        return nativeGenerate(prompt, maximumOutputTokens)
    }

    private external fun nativeEngineId(): String
    private external fun nativeArtifactSha256(): String
    private external fun nativeLoadModel(localPath: String, contextTokens: Int): Boolean
    private external fun nativeUnloadModel()
    private external fun nativeGenerate(prompt: String, maximumOutputTokens: Int): String
}
