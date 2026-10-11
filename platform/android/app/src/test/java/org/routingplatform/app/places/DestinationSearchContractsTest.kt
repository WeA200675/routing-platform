package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint

class DestinationSearchContractsTest {

    @Test
    fun displayTextIncludesSecondaryAddress() {
        val result =
            DestinationSearchResult(
                id =
                    "result-1",

                primaryLabel =
                    "Bahnhof",

                secondaryLabel =
                    "Vaduz, Liechtenstein",

                point =
                    RoutePoint(
                        latitude =
                            47.1410,

                        longitude =
                            9.5209,
                    ),
            )

        assertEquals(
            "Bahnhof — Vaduz, Liechtenstein",
            result.displayText,
        )
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            DestinationSearchResult(
                id =
                    "invalid",

                primaryLabel =
                    "Ungültig",

                secondaryLabel =
                    null,

                point =
                    RoutePoint(
                        latitude =
                            95.0,

                        longitude =
                            9.5,
                    ),
            )
        }
    }
}
