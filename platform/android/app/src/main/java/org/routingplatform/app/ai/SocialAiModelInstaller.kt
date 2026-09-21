package org.routingplatform.app.ai

import java.io.File

sealed interface SocialAiModelInstallResult {
    data class Installed(val artifact: File) : SocialAiModelInstallResult
    data class Rejected(val reason: String) : SocialAiModelInstallResult
}

/** Atomic, integrity-checked promotion of a staged GGUF into app-owned storage. */
object SocialAiModelInstaller {
    fun install(
        stagedArtifact: File,
        destination: File,
        metadata: LocalModelArtifactMetadata,
        minimumFreeBytesAfterInstall: Long = 256L * 1024 * 1024,
    ): SocialAiModelInstallResult {
        if (!stagedArtifact.isFile) return SocialAiModelInstallResult.Rejected("Staged model is missing.")
        if (!SocialAiModelIntegrity.verify(stagedArtifact, metadata)) {
            return SocialAiModelInstallResult.Rejected("Staged model integrity verification failed.")
        }
        val parent = destination.parentFile ?: return SocialAiModelInstallResult.Rejected("Invalid model destination.")
        if (!parent.exists() && !parent.mkdirs()) return SocialAiModelInstallResult.Rejected("Model directory cannot be created.")
        if (parent.usableSpace - stagedArtifact.length() < minimumFreeBytesAfterInstall) {
            return SocialAiModelInstallResult.Rejected("Insufficient storage for atomic model installation.")
        }
        val temporary = File(parent, destination.name + ".installing")
        if (temporary.exists() && !temporary.delete()) return SocialAiModelInstallResult.Rejected("Stale model staging file cannot be removed.")
        return try {
            stagedArtifact.inputStream().use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            if (!SocialAiModelIntegrity.verify(temporary, metadata)) {
                temporary.delete()
                SocialAiModelInstallResult.Rejected("Copied model failed integrity verification.")
            } else {
                if (destination.exists() && !destination.delete()) {
                    temporary.delete()
                    SocialAiModelInstallResult.Rejected("Previous model cannot be replaced.")
                } else if (!temporary.renameTo(destination)) {
                    temporary.delete()
                    SocialAiModelInstallResult.Rejected("Atomic model promotion failed.")
                } else {
                    SocialAiModelInstallResult.Installed(destination)
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            SocialAiModelInstallResult.Rejected(error.message ?: "Model installation failed.")
        }
    }
}
