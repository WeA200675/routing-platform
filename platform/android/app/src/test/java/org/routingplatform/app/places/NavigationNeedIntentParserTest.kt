package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationNeedIntentParserTest {
    @Test
    fun parsesGenericNeedsAlongTheRoute() {
        val intent = NavigationNeedIntentParser.parse("Finde eine offene Apotheke ohne großen Umweg")
        assertEquals(PoiCategory.PHARMACY, intent?.category)
        assertTrue(intent?.alongRoute == true)
        assertTrue(intent?.requireOpenNow == true)
        assertEquals(900, intent?.maxDetourSeconds)
    }
}
