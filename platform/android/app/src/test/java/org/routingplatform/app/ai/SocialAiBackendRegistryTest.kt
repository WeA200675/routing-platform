package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocialAiBackendRegistryTest {
    @Test
    fun emptyRegistryFailsClosed() {
        val registry = SocialAiBackendRegistry(emptyList())

        assertEquals(emptyList<String>(), registry.availableBackendIds())
        assertThrows(IllegalStateException::class.java) {
            registry.require("missing")
        }
    }
}
