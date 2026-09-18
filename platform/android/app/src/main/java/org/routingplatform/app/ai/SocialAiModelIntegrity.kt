package org.routingplatform.app.ai

import java.io.File
import java.security.MessageDigest

object SocialAiModelIntegrity {
    fun sha256(file: File): String {
        require(file.isFile) {
            "Model artifact must be a regular file."
        }

        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }

    fun verify(
        file: File,
        metadata: LocalModelArtifactMetadata,
    ): Boolean =
        sha256(file).equals(metadata.sha256, ignoreCase = true)
}
