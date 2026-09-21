package org.routingplatform.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        if (!target.isFile || !SocialAiModelIntegrity.sha256(target).equals(MODEL_SHA256, ignoreCase = true)) {
            val staged = File(context.filesDir, "$MODEL_FILE.staged")
            context.assets.open(MODEL_ASSET).use { input ->
                staged.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            require(SocialAiModelIntegrity.sha256(staged).equals(MODEL_SHA256, ignoreCase = true)) {
                "Das lokale KI-Modell hat die Integritätsprüfung nicht bestanden."
            }
            val backup = File(context.filesDir, "$MODEL_FILE.previous")
            if (backup.exists()) require(backup.delete())
            if (target.exists()) {
                Files.move(
                    target.toPath(),
                    backup.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            try {
                Files.move(
                    staged.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
                require(SocialAiModelIntegrity.sha256(target).equals(MODEL_SHA256, ignoreCase = true)) {
                    "Das aktivierte lokale KI-Modell hat die Integritätsprüfung nicht bestanden."
                }
                if (backup.exists()) backup.delete()
            } catch (error: Exception) {
                if (target.exists()) {
                    require(target.delete()) {
                        "Failed to remove rejected model artifact."
                    }
                }
                if (backup.exists()) {
                    Files.move(
                        backup.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                throw error
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
        private const val CONTEXT_TOKENS = 4096
        private const val MINIMUM_AVAILABLE_MEMORY_BYTES = 768L * 1024 * 1024
    }
}
