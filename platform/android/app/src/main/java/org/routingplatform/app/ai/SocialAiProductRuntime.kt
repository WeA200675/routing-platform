package org.routingplatform.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File

/**
 * Production owner for the pinned local routing model. It has no networking
 * dependency and only admits the exact model artifact embedded in the signed RC.
 */
class SocialAiProductRuntime(
    private val context: Context,
) : AutoCloseable {
    private var engine: JniSocialAiNativeEngine? = null
    private var model: File? = null

    @Synchronized
    fun routing(): SocialAiProductionRouting {
        val active = engine ?: load()
        return SocialAiProductionRouting(active)
    }

    @Synchronized
    private fun load(): JniSocialAiNativeEngine {
        val memory = ActivityManager.MemoryInfo().also {
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
        }
        require(memory.availMem >= MINIMUM_AVAILABLE_MEMORY_BYTES) {
            "Nicht genügend freier Speicher für die lokale KI."
        }

        val target = File(context.filesDir, MODEL_FILE)
        if (!target.isFile || target.length() != MODEL_SIZE_BYTES || !SocialAiModelIntegrity.verify(target, MODEL_METADATA)) {
            require(context.filesDir.usableSpace >= MODEL_SIZE_BYTES * 2L + MINIMUM_FREE_STORAGE_AFTER_INSTALL_BYTES) {
                "Nicht genügend Speicherplatz für die verifizierte lokale KI-Installation."
            }
            val staged = File(context.filesDir, "$MODEL_FILE.staged")
            runCatching {
                context.assets.open(MODEL_ASSET).use { input ->
                    staged.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }.getOrElse { error ->
                staged.delete()
                throw IllegalStateException("Lokales KI-Modell konnte nicht bereitgestellt werden.", error)
            }

            try {
            when (
                val install =
                    SocialAiModelInstaller.install(
                        stagedArtifact = staged,
                        destination = target,
                        metadata = MODEL_METADATA,
                        minimumFreeBytesAfterInstall = MINIMUM_FREE_STORAGE_AFTER_INSTALL_BYTES,
                        expectedSizeBytes = MODEL_SIZE_BYTES,
                    )
            ) {
                is SocialAiModelInstallResult.Installed -> Unit
                is SocialAiModelInstallResult.Rejected ->
                    throw IllegalStateException(install.reason)
            }
            } finally {
                staged.delete()
            }
        }

        val loadedEngine = JniSocialAiNativeEngine()
        require(loadedEngine.loadModel(target.absolutePath, CONTEXT_TOKENS)) {
            "Die lokale KI-Runtime hat das verifizierte Modell abgelehnt."
        }
        model = target
        engine = loadedEngine
        return loadedEngine
    }

    @Synchronized
    override fun close() {
        engine?.unloadModel()
        engine = null
        model = null
    }

    companion object {
        private const val MODEL_ASSET = "g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
        private const val MODEL_FILE = "social-ai-qwen2.5-1.5b-q4_k_m.gguf"
        private const val MODEL_SHA256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370"
        private const val MODEL_SIZE_BYTES = 986048768L
        private const val CONTEXT_TOKENS = 4096
        private const val MINIMUM_AVAILABLE_MEMORY_BYTES = 768L * 1024 * 1024
        private const val MINIMUM_FREE_STORAGE_AFTER_INSTALL_BYTES = 256L * 1024 * 1024
        private val MODEL_METADATA =
            LocalModelArtifactMetadata(
                modelId = "Qwen2.5-1.5B-Instruct",
                revision = "ec7a7a1a4d6ff0f562a01d41672912bacd9a56b3",
                licenseSpdx = "Apache-2.0",
                sha256 = MODEL_SHA256,
                sourceUrl = "https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF",
            )
    }
}
