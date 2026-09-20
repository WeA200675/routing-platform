package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SocialAiRoutingIntentParserTest {
    private val engine = object : SocialAiNativeEngine {
        override val engineId = "test"
        override val artifactSha256 = "0".repeat(64)
        override fun loadModel(localPath: String, contextTokens: Int) = true
        override fun unloadModel() = Unit
        override fun generate(prompt: String, maximumOutputTokens: Int) = ""
    }
    private val parser = SocialAiRoutingIntentParser(engine)

    @Test fun parsesStrictRoutingIntent() {
        assertEquals(
            SocialAiRoutingIntent("home", listOf("motorway"), "supermarket"),
            parser.parseStrict("DESTINATION=home\nAVOID=motorway\nVIA=supermarket"),
        )
    }

    @Test fun rejectsProseOrMalformedOutput() {
        assertNull(parser.parseStrict("Natürlich! DESTINATION=home"))
        assertNull(parser.parseStrict("DESTINATION=home\nAVOID=motorway"))
        assertNull(parser.parseStrict("DESTINATION=home now\nAVOID=\nVIA="))
    }
}
