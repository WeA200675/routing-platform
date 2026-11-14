package org.routingplatform.app.ai

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface SocialAiModelInstallResult {
    data class Installed(val artifact: File) : SocialAiModelInstallResult
    data class Rejected(val reason: String) : SocialAiModelInstallResult
}

/** Installs only the exact pinned GGUF, preserving the last known good model on failure. */
object SocialAiModelInstaller {
    fun downloadAndInstall(
        url: String,
        destination: File,
        metadata: LocalModelArtifactMetadata,
        expectedSizeBytes: Long,
        minimumFreeBytesAfterInstall: Long = 256L * 1024 * 1024,
        connectTimeoutMillis: Int = 15_000,
        readTimeoutMillis: Int = 60_000,
        onProgress: (receivedBytes: Long, expectedBytes: Long) -> Unit = { _, _ -> },
    ): SocialAiModelInstallResult {
        if (!url.startsWith("https://")) return rejected("Model source must use HTTPS.")
        if (expectedSizeBytes <= 0 || expectedSizeBytes > MAX_ARTIFACT_BYTES) return rejected("Pinned model size is outside allowed limits.")
        val parent = destination.parentFile ?: return rejected("Invalid model destination.")
        if (!parent.exists() && !parent.mkdirs()) return rejected("Model directory cannot be created.")
        if (parent.usableSpace < expectedSizeBytes + minimumFreeBytesAfterInstall) return rejected("Insufficient storage for model download.")
        val staged = File(parent, destination.name + ".download")
        val temporary = File(parent, destination.name + ".installing")
        val backup = File(parent, destination.name + ".previous")
        staged.delete()
        temporary.delete()
        backup.delete()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                setRequestProperty("User-Agent", "routing-platform-local-llm/1")
            }
            connection.connect()
            if (connection.responseCode !in 200..299) return rejected("Model download returned HTTP ${connection.responseCode}.")
            val contentLength = connection.contentLengthLong
            if (contentLength >= 0 && contentLength != expectedSizeBytes) return rejected("Model download size header does not match the pinned artifact.")
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            connection.inputStream.use { input ->
                staged.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        if (received > expectedSizeBytes) throw IllegalStateException("Downloaded model exceeds the pinned size.")
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        onProgress(received, expectedSizeBytes)
                    }
                }
            }
            if (received != expectedSizeBytes) throw IllegalStateException("Downloaded model is incomplete.")
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(metadata.sha256, ignoreCase = true)) throw IllegalStateException("Downloaded model SHA-256 verification failed.")
            installVerified(staged, destination, metadata, expectedSizeBytes, minimumFreeBytesAfterInstall)
        } catch (error: Exception) {
            staged.delete()
            temporary.delete()
            if (!destination.exists() && backup.exists()) runCatching { move(backup, destination) }
            rejected(error.message ?: "Model download or installation failed.")
        } finally {
            connection?.disconnect()
            staged.delete()
            temporary.delete()
            if (destination.exists()) backup.delete()
        }
    }

    fun install(
        stagedArtifact: File,
        destination: File,
        metadata: LocalModelArtifactMetadata,
        minimumFreeBytesAfterInstall: Long = 256L * 1024 * 1024,
        expectedSizeBytes: Long? = null,
    ): SocialAiModelInstallResult {
        if (!stagedArtifact.isFile) return rejected("Staged model is missing.")
        if (expectedSizeBytes != null && stagedArtifact.length() != expectedSizeBytes) return rejected("Staged model size verification failed.")
        if (!verify(stagedArtifact, metadata)) return rejected("Staged model integrity verification failed.")
        return installVerified(stagedArtifact, destination, metadata, expectedSizeBytes, minimumFreeBytesAfterInstall)
    }

    private fun installVerified(
        stagedArtifact: File,
        destination: File,
        metadata: LocalModelArtifactMetadata,
        expectedSizeBytes: Long?,
        minimumFreeBytesAfterInstall: Long,
    ): SocialAiModelInstallResult {
        val parent = destination.parentFile ?: return rejected("Invalid model destination.")
        if (!parent.exists() && !parent.mkdirs()) return rejected("Model directory cannot be created.")
        val stagedIsLocal = runCatching {
            stagedArtifact.canonicalFile.parentFile == parent.canonicalFile
        }.getOrDefault(false)
        val temporary = File(parent, destination.name + ".installing")
        val backup = File(parent, destination.name + ".previous")
        temporary.delete()
        backup.delete()
        val promotionSource = if (stagedIsLocal) stagedArtifact else temporary
        if (!stagedIsLocal && parent.usableSpace < stagedArtifact.length() + minimumFreeBytesAfterInstall) {
            return rejected("Insufficient storage for atomic model installation.")
        }
        if (stagedIsLocal && parent.usableSpace < minimumFreeBytesAfterInstall) {
            return rejected("Insufficient free storage remains after model download.")
        }
        try {
            if (!stagedIsLocal) {
                stagedArtifact.inputStream().use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
            }
            if (expectedSizeBytes != null && promotionSource.length() != expectedSizeBytes) {
                return rejected("Prepared model size verification failed.")
            }
            if (!verify(promotionSource, metadata)) return rejected("Prepared model failed integrity verification.")
            if (destination.exists()) move(destination, backup)
            try {
                move(promotionSource, destination)
            } catch (error: Exception) {
                if (backup.exists()) move(backup, destination)
                throw error
            }
            if ((expectedSizeBytes != null && destination.length() != expectedSizeBytes) || !verify(destination, metadata)) {
                destination.delete()
                if (backup.exists()) move(backup, destination)
                return rejected("Promoted model failed integrity verification; previous model restored.")
            }
            backup.delete()
            return SocialAiModelInstallResult.Installed(destination)
        } catch (error: Exception) {
            temporary.delete()
            if (!destination.exists() && backup.exists()) runCatching { move(backup, destination) }
            return rejected(error.message ?: "Model installation failed.")
        } finally {
            temporary.delete()
            if (destination.exists()) backup.delete()
        }
    }

    private fun verify(file: File, metadata: LocalModelArtifactMetadata): Boolean =
        runCatching { SocialAiModelIntegrity.sha256(file).equals(metadata.sha256, ignoreCase = true) }.getOrDefault(false)

    private fun move(source: File, target: File) {
        try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
    }

    private fun rejected(reason: String) = SocialAiModelInstallResult.Rejected(reason)

    private const val MAX_ARTIFACT_BYTES = 4L * 1024 * 1024 * 1024
}
