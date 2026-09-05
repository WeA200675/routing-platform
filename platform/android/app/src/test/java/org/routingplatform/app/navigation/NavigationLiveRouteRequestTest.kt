package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveRouteRequestTest {

    @Test
    fun requestJsonCarriesRuntimeOriginDestinationViaAndFamily() {
        val request =
            NavigationRouteRequest(
                origin =
                    RoutePoint(
                        latitude =
                            47.1,

                        longitude =
                            9.5,
                    ),

                destination =
                    RoutePoint(
                        latitude =
                            47.2,

                        longitude =
                            9.6,
                    ),

                viaPoints =
                    listOf(
                        RoutePoint(
                            latitude =
                                47.15,

                            longitude =
                                9.55,
                        )
                    ),

                family =
                    NavigationRouteFamily.MajorRoads,
            )

        assertEquals(
            """{"origin":{"latitude":47.1,"longitude":9.5},"destination":{"latitude":47.2,"longitude":9.6},"viaPoints":[{"latitude":47.15,"longitude":9.55}],"family":"major_roads"}""",

            NavigationRouteRequestJson
                .encode(
                    request
                ),
        )
    }
}