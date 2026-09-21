package org.routingplatform.app.ai

import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiModelInstallerTest {
    @Test
    fun installsVerifiedArtifact() {
        val dir = createTempDirectory("model-install").toFile()
        val staged = File(dir, "staged.gguf").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val destination = File(dir, "model.gguf")
        val result = SocialAiModelInstaller.install(staged, destination, metadata(staged), 0)
        assertTrue(result is SocialAiModelInstallResult.Installed)
        assertArrayEquals(staged.readBytes(), destination.readBytes())
    }

    @Test
    fun rejectsCorruptStagedArtifactAndPreservesPreviousModel() {
        val dir = createTempDirectory("model-rollback").toFile()
        val previous = byteArrayOf(9, 8, 7)
        val destination = File(dir, "model.gguf").apply { writeBytes(previous) }
        val staged = File(dir, "staged.gguf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val wrongMetadata = metadata(staged).copy(sha256 = "0".repeat(64))
        val result = SocialAiModelInstaller.install(staged, destination, wrongMetadata, 0)
        assertTrue(result is SocialAiModelInstallResult.Rejected)
        assertArrayEquals(previous, destination.readBytes())
    }

    private fun metadata(file: File) = LocalModelArtifactMetadata(
        family = "test",
        quantization = "test",
        artifactFileName = file.name,
        artifactBytes = file.length(),
        sha256 = sha256(file),
        license = "test",
        sourceRepository = "test",
        sourceRevision = "test",
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
