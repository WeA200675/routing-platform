package org.routingplatform.app.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.routingplatform.app.navigation.RoutePoint

class FavoriteDestinationCodecTest {

    @Test
    fun homeWorkAndCustomFavoritesRoundTrip() {
        val collection =
            FavoriteDestinationCollection
                .empty(
                    "driver-a"
                )
                .withHome(
                    RoutePoint(
                        latitude =
                            47.1410,

                        longitude =
                            9.5209,
                    )
                )
                .withWork(
                    RoutePoint(
                        latitude =
                            47.1660,

                        longitude =
                            9.5100,
                    )
                )
                .addCustom(
                    id =
                        "custom-garage",

                    label =
                        "Werkstatt",

                    point =
                        RoutePoint(
                            latitude =
                                47.1500,

                            longitude =
                                9.5300,
                        ),
                )

        val decoded =
            FavoriteDestinationCodec
                .decode(
                    FavoriteDestinationCodec
                        .encode(
                            collection
                        )
                )

        assertEquals(
            collection,
            decoded,
        )

        assertEquals(
            "Zuhause",
            decoded.home?.label,
        )

        assertEquals(
            "Arbeit",
            decoded.work?.label,
        )

        assertEquals(
            "Werkstatt",
            decoded.custom.single().label,
        )
    }

    @Test
    fun reservedFavoriteCanBeRemovedWithoutTouchingOthers() {
        val collection =
            FavoriteDestinationCollection
                .empty(
                    "driver-a"
                )
                .withHome(
                    RoutePoint(
                        47.1410,
                        9.5209,
                    )
                )
                .withWork(
                    RoutePoint(
                        47.1660,
                        9.5100,
                    )
                )
                .remove(
                    "home"
                )

        assertNull(
            collection.home
        )

        assertEquals(
            "Arbeit",
            collection.work?.label,
        )
    }

    @Test
    fun invalidPayloadFailsClosed() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            FavoriteDestinationCodec
                .decode(
                    "not-a-favorite-payload!"
                )
        }
    }
}
