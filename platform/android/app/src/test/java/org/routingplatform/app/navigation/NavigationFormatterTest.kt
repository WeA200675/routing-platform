package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationFormatterTest {

    @Test
    fun formatsMetersAndKilometers() {
        assertEquals(
            "750 m",
            NavigationFormatter.distance(
                750.2
            ),
        )

        assertEquals(
            "3,2 km",
            NavigationFormatter.distance(
                3174.0
            ),
        )
    }

    @Test
    fun formatsMinutesAndHours() {
        assertEquals(
            "4 min",
            NavigationFormatter.duration(
                242.0
            ),
        )

        assertEquals(
            "1 h 05 min",
            NavigationFormatter.duration(
                3900.0
            ),
        )
    }
}
