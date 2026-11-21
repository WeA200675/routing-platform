package org.routingplatform.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File

sealed interface SocialAiSetupState {
    data object Ready : SocialAiSetupState
    data object ModelMissing : SocialAiSetupState
    data object NativeRuntimeMissing : SocialAiSetupState
    data class Unavailable(val reason: String) : SocialAiSetupState
}

/** Opt-in, local-only model owner. No model download or native library load occurs at app startup. */
class SocialAiProductRuntime(private val context: Context) : AutoCloseable {
    private var engine: JniSocialAiNativeEngine? = null
    private var backend: VerifiedGgufLocalBackend? = null

    fun modelFile(): File = File(context.filesDir, MODEL_FILE)

    fun setupState(): SocialAiSetupState = when {
        !runCatching { System.loadLibrary("social_ai_llama") }.isSuccess -> SocialAiSetupState.NativeRuntimeMissing
        !modelFile().isFile || modelFile().length() != MODEL_BYTES -> SocialAiSetupState.ModelMissing
        !runCatching { SocialAiModelIntegrity.verify(modelFile(), MODEL_METADATA) }.getOrDefault(false) ->
            SocialAiSetupState.Unavailable("Das gespeicherte Modell besteht die Integritätsprüfung nicht.")
        else -> SocialAiSetupState.Ready
    }

    fun installModel(onProgress: (Long, Long) -> Unit = { _, _ -> }): SocialAiModelInstallResult =
        SocialAiModelInstaller.downloadAndInstall(
            url = MODEL_URL,
            destination = modelFile(),
            metadata = MODEL_METADATA,
            expectedSizeBytes = MODEL_BYTES,
            onProgress = onProgress,
        )

    @Synchronized
    fun generate(
        input: SocialAiRuntimeInput,
        text: String,
        rememberedContext: List<SocialAiKnowledge> = emptyList(),
        maximumOutputTokens: Int = 160,
    ): SocialAiTextGenerationResult? = runCatching {
        ensureBackend().let { active ->
            SocialAiTextGenerationCoordinator(active).generateOrNull(
                input = input,
                userText = text,
                rememberedContext = rememberedContext.take(32),
                maximumOutputTokens = maximumOutputTokens.coerceIn(1, MAX_OUTPUT_TOKENS),
            )
        }
    }.getOrNull()

    @Synchronized
    private fun ensureBackend(): VerifiedGgufLocalBackend {
        backend?.let { return it }
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        require(memory.availMem >= MINIMUM_AVAILABLE_MEMORY_BYTES) { "Nicht genügend freier Arbeitsspeicher für die lokale KI." }
        val artifact = modelFile()
        require(artifact.isFile && artifact.length() == MODEL_BYTES) { "Das lokale KI-Modell ist noch nicht installiert." }
        require(SocialAiModelIntegrity.verify(artifact, MODEL_METADATA)) { "Die Modellprüfung ist fehlgeschlagen." }
        val native = engine ?: JniSocialAiNativeEngine().also { engine = it }
        require(native.engineId == RUNTIME_REVISION) { "Die native KI-Laufzeit ist nicht die freigegebene Version." }
        require(native.artifactSha256.equals(RUNTIME_SOURCE_SHA256, ignoreCase = true)) { "Die native KI-Laufzeit besteht die Herkunftsprüfung nicht." }
        val active = VerifiedGgufLocalBackend(
            backendId = BACKEND_ID,
            runtimeMetadata = RUNTIME_METADATA,
            modelMetadata = MODEL_METADATA,
            engine = native,
        )
        require(active.load(artifact)) { "Das lokale Modell konnte nicht geladen werden." }
        backend = active
        return active
    }

    @Synchronized
    override fun close() {
        backend?.close()
        backend = null
        engine = null
    }

    companion object {
        const val RUNTIME_REVISION = "b23701f77d47dad9de834d59ebfcbe25c9e8b46f"
        const val MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_BYTES = 986_048_768L
        const val MODEL_URL = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/ec7a7a1a4d6ff0f562a01d41672912bacd9a56b3/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        const val MINIMUM_AVAILABLE_MEMORY_BYTES = 768L * 1024 * 1024
        const val MAX_OUTPUT_TOKENS = 512
        private const val BACKEND_ID = "qwen2.5-1.5b-local"
        private const val RUNTIME_SOURCE_SHA256 = "c443bb6cef2e1779592c6b854145f6ec5611c9fb8825284c2883424af94de77e"
        private const val MODEL_SHA256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370"
        val MODEL_METADATA = LocalModelArtifactMetadata(
            modelId = "Qwen2.5-1.5B-Instruct",
            revision = "ec7a7a1a4d6ff0f562a01d41672912bacd9a56b3",
            licenseSpdx = "Apache-2.0",
            sha256 = MODEL_SHA256,
            sourceUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF",
        )
        private val RUNTIME_METADATA = OpenSourceComponentMetadata(
            componentId = "ggml-org/llama.cpp",
            revision = RUNTIME_REVISION,
            licenseSpdx = "MIT",
            sourceUrl = "https://github.com/ggml-org/llama.cpp",
        )
    }
}
