package org.routingplatform.app.ai

interface SocialAiNativeEngine {
    val engineId: String
    val artifactSha256: String
    fun loadModel(localPath: String, contextTokens: Int): Boolean
    fun unloadModel()
    fun generate(prompt: String, maximumOutputTokens: Int): String
}

interface SocialAiRuntimeBuildIdentified {
    val runtimeBuildId: String
}

interface SocialAiRuntimeArtifactIdentified {
    val runtimeArtifactSha256: String
}
