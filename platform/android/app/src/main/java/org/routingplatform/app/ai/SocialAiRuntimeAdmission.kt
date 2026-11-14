package org.routingplatform.app.ai

import android.app.ActivityManager
import android.content.Context
import java.io.File

enum class SocialAiAdmissionState {
    READY,
    MODEL_MISSING,
    MODEL_HASH_MISMATCH,
    NATIVE_RUNTIME_UNAVAILABLE,
    INSUFFICIENT_MEMORY,
    INSUFFICIENT_STORAGE,
    REJECTED,
}

data class SocialAiAdmissionResult(
    val state: SocialAiAdmissionState,
    val detail: String,
    val modelPath: String? = null,
    val modelSha256: String? = null,
) {
    val accepted: Boolean get() = state == SocialAiAdmissionState.READY
}

object SocialAiRuntimeAdmission {
    private const val MODEL_ASSET = "g620/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
    private const val MODEL_FILE = "social-ai-qwen2.5-1.5b-q4_k_m.gguf"
    private const val MODEL_SHA256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370"
    private const val MODEL_SIZE_BYTES = 986048768L
    private const val MINIMUM_AVAILABLE_MEMORY_BYTES = 768L * 1024 * 1024
    private const val MINIMUM_FREE_STORAGE_BYTES = 256L * 1024 * 1024

    fun probe(context: Context): SocialAiAdmissionResult {
        val appContext = context.applicationContext
        val memory = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
            ?.getMemoryInfo(memory)
        if (memory.availMem in 1 until MINIMUM_AVAILABLE_MEMORY_BYTES) {
            return SocialAiAdmissionResult(
                SocialAiAdmissionState.INSUFFICIENT_MEMORY,
                "Zu wenig verfügbarer Arbeitsspeicher für die lokale KI.",
            )
        }

        val model = File(appContext.filesDir, MODEL_FILE)
        if (!model.isFile) {
            val packaged = runCatching {
                appContext.assets.open(MODEL_ASSET).use { }
            }.isSuccess
            return if (packaged) {
                SocialAiAdmissionResult(
                    SocialAiAdmissionState.REJECTED,
                    "Modell ist verpackt, aber noch nicht installiert.",
                )
            } else {
                SocialAiAdmissionResult(
                    SocialAiAdmissionState.MODEL_MISSING,
                    "Verifiziertes lokales Modell fehlt.",
                )
            }
        }

        if (model.length() != MODEL_SIZE_BYTES) {
            return SocialAiAdmissionResult(
                SocialAiAdmissionState.MODEL_HASH_MISMATCH,
                "Modellgröße stimmt nicht mit dem Release-Manifest überein.",
                modelPath = model.absolutePath,
            )
        }

        val hash = runCatching { SocialAiModelIntegrity.sha256(model) }.getOrNull()
            ?: return SocialAiAdmissionResult(
                SocialAiAdmissionState.REJECTED,
                "Modellprüfung konnte nicht ausgeführt werden.",
                modelPath = model.absolutePath,
            )
        if (!hash.equals(MODEL_SHA256, ignoreCase = true)) {
            return SocialAiAdmissionResult(
                SocialAiAdmissionState.MODEL_HASH_MISMATCH,
                "Modellprüfsumme stimmt nicht.",
                modelPath = model.absolutePath,
                modelSha256 = hash,
            )
        }

        if (appContext.filesDir.usableSpace < MINIMUM_FREE_STORAGE_BYTES) {
            return SocialAiAdmissionResult(
                SocialAiAdmissionState.INSUFFICIENT_STORAGE,
                "Zu wenig freier Speicher für lokale KI-Ausführung.",
                modelPath = model.absolutePath,
                modelSha256 = hash,
            )
        }

        val native = runCatching { JniSocialAiNativeEngine() }.getOrNull()
            ?: return SocialAiAdmissionResult(
                SocialAiAdmissionState.NATIVE_RUNTIME_UNAVAILABLE,
                "Native lokale KI-Runtime ist nicht verfügbar.",
                modelPath = model.absolutePath,
                modelSha256 = hash,
            )

        native.unloadModel()
        return SocialAiAdmissionResult(
            SocialAiAdmissionState.READY,
            "Modell und Native-Runtime wurden erfolgreich geprüft.",
            modelPath = model.absolutePath,
            modelSha256 = hash,
        )
    }
}
