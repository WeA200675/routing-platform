package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiModelLeaseTest {
    private val metadata = LocalModelArtifactMetadata(
        "test/model", "immutable-revision", "MIT", "a".repeat(64),
        "https://example.invalid/model"
    )

    @Test
    fun replacingModelRevokesOldLease() {
        val registry = SocialAiModelLeaseRegistry()
        val first = registry.activate(metadata)
        val second = registry.activate(metadata.copy(revision = "immutable-revision-2"))
        assertThrows(IllegalStateException::class.java) { registry.requireActive(first) }
        registry.requireActive(second)
        assertTrue(second.generation > first.generation)
    }

    @Test
    fun revokeFailsClosed() {
        val registry = SocialAiModelLeaseRegistry()
        val lease = registry.activate(metadata)
        registry.revoke()
        assertThrows(IllegalStateException::class.java) { registry.requireActive(lease) }
        assertEquals(null, registry.current())
    }
}
