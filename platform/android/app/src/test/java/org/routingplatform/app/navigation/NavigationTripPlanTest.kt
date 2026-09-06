package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationTripPlanTest {

    private val destination =
        NavigationTripStop(
            point =
                RoutePoint(
                    latitude =
                        47.1660,

                    longitude =
                        9.5100,
                ),

            label =
                "Ziel",
        )

    @Test
    fun viaPointsCanBeAddedMovedAndRemoved() {
        val first =
            NavigationTripStop(
                RoutePoint(
                    47.1500,
                    9.5200,
                ),
                "A",
            )

        val second =
            NavigationTripStop(
                RoutePoint(
                    47.1550,
                    9.5150,
                ),
                "B",
            )

        val plan =
            NavigationTripPlan(
                destination
            )
                .appendVia(
                    first
                )
                .appendVia(
                    second
                )
                .moveViaUp(
                    1
                )

        assertEquals(
            listOf(
                second,
                first,
            ),
            plan.viaPoints,
        )

        assertEquals(
            listOf(
                second,
            ),
            plan
                .removeVia(
                    1
                )
                .viaPoints,
        )
    }

    @Test
    fun routeRequestPreservesDestinationViaOrderAndFamily() {
        val first =
            NavigationTripStop(
                RoutePoint(
                    47.1500,
                    9.5200,
                )
            )

        val second =
            NavigationTripStop(
                RoutePoint(
                    47.1550,
                    9.5150,
                )
            )

        val plan =
            NavigationTripPlan(
                destination =
                    destination,

                viaPoints =
                    listOf(
                        first,
                        second,
                    ),
            )

        val origin =
            RoutePoint(
                47.1410,
                9.5209,
            )

        val request =
            plan.toRouteRequest(
                origin =
                    origin,

                family =
                    NavigationRouteFamily.Comfort,
            )

        assertEquals(
            origin,
            request.origin,
        )

        assertEquals(
            destination.point,
            request.destination,
        )

        assertEquals(
            listOf(
                first.point,
                second.point,
            ),
            request.viaPoints,
        )

        assertEquals(
            NavigationRouteFamily.Comfort,
            request.family,
        )
    }

    @Test
    fun moreThanSixteenViaPointsAreRejected() {
        val points =
            (0 until 17)
                .map {
                        index ->

                    NavigationTripStop(
                        RoutePoint(
                            latitude =
                                47.10 +
                                    index *
                                    0.001,

                            longitude =
                                9.50,
                        )
                    )
                }

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            NavigationTripPlan(
                destination =
                    destination,

                viaPoints =
                    points,
            )
        }
    }
}
