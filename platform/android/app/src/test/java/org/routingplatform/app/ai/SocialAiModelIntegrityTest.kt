package org.routingplatform.app.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiModelIntegrityTest {
    @Test
    fun verifiesExactSha256() {
        val file = File.createTempFile("social-ai-model", ".bin")
        try {
            file.writeText("routing-platform")
            val digest = SocialAiModelIntegrity.sha256(file)
            val metadata =
                LocalModelArtifactMetadata(
                    modelId = "test/model",
                    revision = "immutable-test",
                    licenseSpdx = "Apache-2.0",
                    sha256 = digest,
                    sourceUrl = "https://example.invalid/model",
                )

            assertTrue(SocialAiModelIntegrity.verify(file, metadata))
            file.appendText("-changed")
            assertFalse(SocialAiModelIntegrity.verify(file, metadata))
        } finally {
            file.delete()
        }
    }
}
