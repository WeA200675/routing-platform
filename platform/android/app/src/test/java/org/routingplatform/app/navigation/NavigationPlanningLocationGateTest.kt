package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPlanningLocationGateTest {

    @Test
    fun freshPreciseSampleIsAccepted() {
        val gate =
            NavigationPlanningLocationGate()

        val accepted =
            gate.accept(
                sample =
                    sample(
                        accuracyM =
                            8.0,

                        elapsedRealtimeNanos =
                            1_000_000_000L,
                    ),

                nowElapsedRealtimeNanos =
                    2_000_000_000L,
            )

        assertNotNull(
            accepted
        )

        assertEquals(
            8.0,
            accepted!!
                .horizontalAccuracyM,
            0.0,
        )
    }

    @Test
    fun staleSampleIsRejected() {
        val gate =
            NavigationPlanningLocationGate()

        assertNull(
            gate.accept(
                sample =
                    sample(
                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            1_000_000_000L,
                    ),

                nowElapsedRealtimeNanos =
                    21_000_000_001L,
            )
        )
    }

    @Test
    fun futureTimestampIsRejected() {
        val gate =
            NavigationPlanningLocationGate()

        assertNull(
            gate.accept(
                sample =
                    sample(
                        accuracyM =
                            5.0,

                        elapsedRealtimeNanos =
                            10_000L,
                    ),

                nowElapsedRealtimeNanos =
                    9_999L,
            )
        )
    }

    @Test
    fun inaccurateOrMissingAccuracyIsRejected() {
        val gate =
            NavigationPlanningLocationGate()

        assertNull(
            gate.accept(
                sample =
                    sample(
                        accuracyM =
                            100.01,

                        elapsedRealtimeNanos =
                            1_000L,
                    ),

                nowElapsedRealtimeNanos =
                    2_000L,
            )
        )

        assertNull(
            gate.accept(
                sample =
                    sample(
                        accuracyM =
                            null,

                        elapsedRealtimeNanos =
                            1_000L,
                    ),

                nowElapsedRealtimeNanos =
                    2_000L,
            )
        )
    }

    @Test
    fun newerGenerationInvalidatesOlderCallback() {
        val gate =
            NavigationAsyncGenerationGate()

        val first =
            gate.begin()

        val second =
            gate.begin()

        assertFalse(
            gate.isCurrent(
                first
            )
        )

        assertFalse(
            gate.consume(
                first
            )
        )

        assertTrue(
            gate.isCurrent(
                second
            )
        )

        assertTrue(
            gate.consume(
                second
            )
        )

        assertFalse(
            gate.consume(
                second
            )
        )
    }

    @Test
    fun cancellationInvalidatesCurrentCallback() {
        val gate =
            NavigationAsyncGenerationGate()

        val token =
            gate.begin()

        gate.cancel()

        assertFalse(
            gate.isCurrent(
                token
            )
        )

        assertFalse(
            gate.consume(
                token
            )
        )
    }

    private fun sample(
        accuracyM:
            Double?,

        elapsedRealtimeNanos:
            Long,
    ): NavigationLocationSample =
        NavigationLocationSample(
            position =
                RoutePoint(
                    latitude =
                        47.1410,

                    longitude =
                        9.5209,
                ),

            horizontalAccuracyM =
                accuracyM,

            elapsedRealtimeNanos =
                elapsedRealtimeNanos,

            provider =
                "test",
        )
}