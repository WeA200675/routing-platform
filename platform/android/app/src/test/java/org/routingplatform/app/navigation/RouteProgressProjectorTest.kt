package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressProjectorTest {

    private val route =
        listOf(
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.0,
            ),
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.001,
            ),
            RoutePoint(
                latitude =
                    0.0,

                longitude =
                    0.002,
            ),
        )

    @Test
    fun projectsPointOntoClosestSegment() {
        val projection =
            projectPointToRoute(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.0001,

                        longitude =
                            0.0005,
                    ),
            )

        assertEquals(
            0,
            projection.shapeSegmentIndex,
        )

        assertEquals(
            0.5,
            projection.segmentFraction,
            0.001,
        )

        assertEquals(
            0.0,
            projection
                .projectedPosition
                .latitude,
            0.000001,
        )

        assertEquals(
            0.0005,
            projection
                .projectedPosition
                .longitude,
            0.000001,
        )

        assertTrue(
            projection.distanceToRouteM in
                11.0..11.3
        )
    }

    @Test
    fun canonicalizesSharedVertexToFollowingSegment() {
        val projection =
            projectPointToRoute(
                points =
                    route,

                position =
                    route[1],
            )

        assertEquals(
            1,
            projection.shapeSegmentIndex,
        )

        assertEquals(
            0.0,
            projection.segmentFraction,
            0.0,
        )

        assertEquals(
            route[1],
            projection.projectedPosition,
        )
    }

    @Test
    fun preservesMonotoneProgressWhenGpsJittersBackward() {
        val projector =
            RouteProgressProjector(
                maxDistanceToRouteM =
                    50.0
            )

        val first =
            projector.project(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.0,

                        longitude =
                            0.0015,
                    ),
            )

        assertNotNull(
            first
        )

        val firstProjection =
            checkNotNull(first)

        assertEquals(
            1,
            firstProjection
                .shapeSegmentIndex,
        )

        assertEquals(
            0.5,
            firstProjection
                .segmentFraction,
            0.001,
        )

        val jittered =
            projector.project(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.0,

                        longitude =
                            0.0014,
                    ),
            )

        assertNotNull(
            jittered
        )

        val jitteredProjection =
            checkNotNull(jittered)

        assertTrue(
            jitteredProjection
                .shapePosition >=
                firstProjection
                    .shapePosition
        )

        assertEquals(
            firstProjection
                .shapePosition,
            jitteredProjection
                .shapePosition,
            0.000001,
        )
    }

    @Test
    fun rejectsPositionOutsideRouteDistanceGate() {
        val projector =
            RouteProgressProjector(
                maxDistanceToRouteM =
                    30.0
            )

        val projection =
            projector.project(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.01,

                        longitude =
                            0.0005,
                    ),
            )

        assertNull(
            projection
        )

        assertNull(
            projector.lastAcceptedProjection
        )
    }

    @Test
    fun rejectedPositionDoesNotDestroyAcceptedProgress() {
        val projector =
            RouteProgressProjector(
                maxDistanceToRouteM =
                    30.0
            )

        val accepted =
            projector.project(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.0,

                        longitude =
                            0.0015,
                    ),
            )

        assertNotNull(
            accepted
        )

        val rejected =
            projector.project(
                points =
                    route,

                position =
                    RoutePoint(
                        latitude =
                            0.01,

                        longitude =
                            0.0016,
                    ),
            )

        assertNull(
            rejected
        )

        assertEquals(
            checkNotNull(accepted)
                .shapePosition,

            checkNotNull(
                projector
                    .lastAcceptedProjection
            ).shapePosition,

            0.0,
        )
    }

    @Test
    fun representsFinalRoutePointAsFinalSegmentEnd() {
        val projection =
            projectPointToRoute(
                points =
                    route,

                position =
                    route.last(),
            )

        assertEquals(
            route.lastIndex - 1,
            projection.shapeSegmentIndex,
        )

        assertEquals(
            1.0,
            projection.segmentFraction,
            0.0,
        )

        assertEquals(
            route.last(),
            projection.projectedPosition,
        )

        assertEquals(
            0.0,
            projection.distanceToRouteM,
            0.001,
        )
    }
}