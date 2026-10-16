package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadsideStopSearchTest {
    @Test
    fun toiletAndAccessibilityTagsAreTriStateAndDoNotGuess() {
        assertEquals(RestroomStatus.Present, RoadsideStopTagParser.restroomStatus(mapOf("toilets" to "yes")))
        assertEquals(RestroomStatus.Absent, RoadsideStopTagParser.restroomStatus(mapOf("toilets" to "no")))
        assertEquals(RestroomStatus.Unknown, RoadsideStopTagParser.restroomStatus(emptyMap()))
        assertEquals(true, RoadsideStopTagParser.wheelchairAccessible(mapOf("toilets:wheelchair" to "yes")))
        assertEquals(false, RoadsideStopTagParser.wheelchairAccessible(mapOf("toilets:wheelchair" to "no")))
        assertNull(RoadsideStopTagParser.wheelchairAccessible(emptyMap()))
    }

    @Test
    fun restroomFiltersOnlyReturnExplicitlyKnownStatuses() {
        assertTrue(RoadsideStopTagParser.matches(RestroomStatus.Unknown, RestroomFilter.Any))
        assertFalse(RoadsideStopTagParser.matches(RestroomStatus.Unknown, RestroomFilter.WithRestroom))
        assertFalse(RoadsideStopTagParser.matches(RestroomStatus.Unknown, RestroomFilter.WithoutRestroom))
        assertTrue(RoadsideStopTagParser.matches(RestroomStatus.Present, RestroomFilter.WithRestroom))
        assertTrue(RoadsideStopTagParser.matches(RestroomStatus.Absent, RestroomFilter.WithoutRestroom))
    }

    @Test
    fun onlyExplicitRestAreaSearchesAreIntercepted() {
        assertEquals(RestroomFilter.WithRestroom, parseRoadsideStopQuery("Raststätte mit WC"))
        assertEquals(RestroomFilter.WithoutRestroom, parseRoadsideStopQuery("Rastplatz ohne Toilette"))
        assertEquals(RestroomFilter.Any, parseRoadsideStopQuery("service area"))
        assertNull(parseRoadsideStopQuery("München Hauptbahnhof"))
    }
}
