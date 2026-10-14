package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteContractGuidanceMetadataTest {
    @Test
    fun signAndLaneMetadataSurviveJsonContractParsing() {
        val route = AndroidNavigationRouteContractJson.parse(
            """{
                "schemaVersion":1,"routeId":"fixture","family":"fastest",
                "distanceM":100,"durationS":10,"geometry":[[48.0,11.0],[48.1,11.1]],
                "maneuvers":[{"type":"exit","instruction":"Ausfahrt nehmen",
                    "streetNames":[],"distanceM":100,"durationS":10,
                    "beginShapeIndex":0,"endShapeIndex":1,"engineType":20,
                    "destinationSign":"A 92 · München",
                    "laneIndications":["↑","↗ ✓"]}],
                "engineName":"fixture","engineVersion":"1",
                "segmentDataStatus":"unavailable","diagnostics":[]
            }""".trimIndent(),
        )

        val maneuver = route.maneuvers.single().toNavigationManeuver()
        assertEquals("A 92 · München", maneuver.destinationSign)
        assertEquals(listOf("↑", "↗ ✓"), maneuver.laneIndications)
    }

    @Test
    fun absentProviderMetadataRemainsAbsent() {
        val route = AndroidNavigationRouteContractJson.parse(
            """{
                "schemaVersion":1,"routeId":"fixture","family":"fastest",
                "distanceM":100,"durationS":10,"geometry":[[48.0,11.0],[48.1,11.1]],
                "maneuvers":[{"type":"exit","instruction":"Ausfahrt nehmen",
                    "streetNames":[],"distanceM":100,"durationS":10,
                    "beginShapeIndex":0,"endShapeIndex":1,"engineType":20}],
                "engineName":"fixture","engineVersion":"1",
                "segmentDataStatus":"unavailable","diagnostics":[]
            }""".trimIndent(),
        )

        val maneuver = route.maneuvers.single().toNavigationManeuver()
        assertEquals(null, maneuver.destinationSign)
        assertEquals(emptyList<String>(), maneuver.laneIndications)
    }
}
