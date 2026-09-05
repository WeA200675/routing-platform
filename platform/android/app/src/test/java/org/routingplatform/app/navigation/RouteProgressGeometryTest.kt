package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteProgressGeometryTest {

    private val points =
        listOf(
            RoutePoint(
                latitude = 0.0,
                longitude = 0.0,
            ),
            RoutePoint(
                latitude = 10.0,
                longitude = 10.0,
            ),
            RoutePoint(
                latitude = 20.0,
                longitude = 20.0,
            ),
            RoutePoint(
                latitude = 30.0,
                longitude = 30.0,
            ),
        )

    @Test
    fun interpolatesInsideSegment() {
        val result =
            splitRouteProgressGeometry(
                points =
                    points,

                shapeSegmentIndex =
                    1,

                segmentFraction =
                    0.5,
            )

        assertEquals(
            15.0,
            result.currentPosition.latitude,
            0.0,
        )

        assertEquals(
            15.0,
            result.currentPosition.longitude,
            0.0,
        )

        assertEquals(
            3,
            result.traveledPoints.size,
        )

        assertEquals(
            3,
            result.remainingPoints.size,
        )

        assertEquals(
            points.first(),
            result.traveledPoints.first(),
        )

        assertEquals(
            points.last(),
            result.remainingPoints.last(),
        )
    }

    @Test
    fun representsRouteStart() {
        val result =
            splitRouteProgressGeometry(
                points =
                    points,

                shapeSegmentIndex =
                    0,

                segmentFraction =
                    0.0,
            )

        assertEquals(
            points.first(),
            result.currentPosition,
        )

        assertEquals(
            points.first(),
            result.traveledPoints.first(),
        )

        assertEquals(
            points.first(),
            result.remainingPoints.first(),
        )
    }

    @Test
    fun representsArrival() {
        val result =
            splitRouteProgressGeometry(
                points =
                    points,

                shapeSegmentIndex =
                    points.lastIndex - 1,

                segmentFraction =
                    1.0,
            )

        assertEquals(
            points.last(),
            result.currentPosition,
        )

        assertEquals(
            points.last(),
            result.traveledPoints.last(),
        )

        assertEquals(
            points.last(),
            result.remainingPoints.first(),
        )
    }
}