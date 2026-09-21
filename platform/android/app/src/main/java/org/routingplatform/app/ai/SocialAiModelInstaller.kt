package org.routingplatform.app.ai

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed interface SocialAiModelInstallResult {
    data class Installed(val artifact: File) : SocialAiModelInstallResult
    data class Rejected(val reason: String) : SocialAiModelInstallResult
}

/** Integrity-checked promotion that preserves the last known-good model on failure. */
object SocialAiModelInstaller {
    fun install(
        stagedArtifact: File,
        destination: File,
        metadata: LocalModelArtifactMetadata,
        minimumFreeBytesAfterInstall: Long = 256L * 1024 * 1024,
    ): SocialAiModelInstallResult {
        if (!stagedArtifact.isFile) return rejected("Staged model is missing.")
        if (!SocialAiModelIntegrity.verify(stagedArtifact, metadata)) return rejected("Staged model integrity verification failed.")
        val parent = destination.parentFile ?: return rejected("Invalid model destination.")
        if (!parent.exists() && !parent.mkdirs()) return rejected("Model directory cannot be created.")
        if (parent.usableSpace - stagedArtifact.length() < minimumFreeBytesAfterInstall) return rejected("Insufficient storage for model installation.")

        val temporary = File(parent, destination.name + ".installing")
        val backup = File(parent, destination.name + ".previous")
        temporary.delete()
        backup.delete()

        return try {
            stagedArtifact.inputStream().use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (!SocialAiModelIntegrity.verify(temporary, metadata)) {
                temporary.delete()
                return rejected("Copied model failed integrity verification.")
            }

            if (destination.exists()) move(destination, backup)
            try {
                move(temporary, destination)
            } catch (error: Exception) {
                if (backup.exists()) move(backup, destination)
                throw error
            }

            if (!SocialAiModelIntegrity.verify(destination, metadata)) {
                destination.delete()
                if (backup.exists()) move(backup, destination)
                return rejected("Promoted model failed integrity verification; previous model restored.")
            }
            backup.delete()
            SocialAiModelInstallResult.Installed(destination)
        } catch (error: Exception) {
            temporary.delete()
            if (!destination.exists() && backup.exists()) runCatching { move(backup, destination) }
            rejected(error.message ?: "Model installation failed.")
        }
    }

    private fun move(source: File, target: File) {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun rejected(reason: String) = SocialAiModelInstallResult.Rejected(reason)
}
