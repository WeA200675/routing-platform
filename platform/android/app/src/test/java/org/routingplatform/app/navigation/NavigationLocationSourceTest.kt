package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class NavigationLocationSourceTest {

    @Test
    fun acceptsValidLocationSample() {
        val sample =
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            47.14,

                        longitude =
                            9.52,
                    ),

                horizontalAccuracyM =
                    4.5,

                elapsedRealtimeNanos =
                    123L,

                provider =
                    "gps",
            )

        assertEquals(
            47.14,
            sample.position.latitude,
            0.0,
        )

        assertEquals(
            4.5,
            checkNotNull(
                sample.horizontalAccuracyM
            ),
            0.0,
        )

        assertEquals(
            "gps",
            sample.provider,
        )
    }

    @Test
    fun allowsUnknownAccuracy() {
        val sample =
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            47.14,

                        longitude =
                            9.52,
                    ),

                horizontalAccuracyM =
                    null,

                elapsedRealtimeNanos =
                    0L,

                provider =
                    null,
            )

        assertNull(
            sample.horizontalAccuracyM
        )
    }

    @Test
    fun rejectsInvalidAccuracy() {
        try {
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            47.14,

                        longitude =
                            9.52,
                    ),

                horizontalAccuracyM =
                    -1.0,

                elapsedRealtimeNanos =
                    1L,

                provider =
                    "gps",
            )

            fail(
                "Expected IllegalArgumentException."
            )
        } catch (
            _: IllegalArgumentException
        ) {
            Unit
        }
    }

    @Test
    fun rejectsInvalidCoordinate() {
        try {
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            95.0,

                        longitude =
                            9.52,
                    ),

                horizontalAccuracyM =
                    1.0,

                elapsedRealtimeNanos =
                    1L,

                provider =
                    "gps",
            )

            fail(
                "Expected IllegalArgumentException."
            )
        } catch (
            _: IllegalArgumentException
        ) {
            Unit
        }
    }
}