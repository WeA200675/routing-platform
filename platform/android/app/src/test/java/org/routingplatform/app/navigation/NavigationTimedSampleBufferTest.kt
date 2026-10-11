package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTimedSampleBufferTest {

    data class Sample(
        val value: Int,
        val timestamp: Long,
    )

    @Test
    fun acceptsStrictlyIncreasingTimestamps() {
        val buffer =
            buffer(
                capacity =
                    4
            )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        1,

                    timestamp =
                        100L,
                )
            )
        )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        2,

                    timestamp =
                        101L,
                )
            )
        )

        assertEquals(
            2,
            buffer.size,
        )

        assertEquals(
            2,
            checkNotNull(
                buffer.latest()
            ).value,
        )
    }

    @Test
    fun rejectsDuplicateTimestamp() {
        val buffer =
            buffer(
                capacity =
                    4
            )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        1,

                    timestamp =
                        100L,
                )
            )
        )

        assertFalse(
            buffer.add(
                Sample(
                    value =
                        2,

                    timestamp =
                        100L,
                )
            )
        )

        assertEquals(
            1,
            buffer.size,
        )
    }

    @Test
    fun rejectsBackwardTimestamp() {
        val buffer =
            buffer(
                capacity =
                    4
            )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        1,

                    timestamp =
                        200L,
                )
            )
        )

        assertFalse(
            buffer.add(
                Sample(
                    value =
                        2,

                    timestamp =
                        199L,
                )
            )
        )

        assertEquals(
            1,
            buffer.size,
        )
    }

    @Test
    fun evictsOldestSamplesAtCapacity() {
        val buffer =
            buffer(
                capacity =
                    2
            )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        1,

                    timestamp =
                        1L,
                )
            )
        )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        2,

                    timestamp =
                        2L,
                )
            )
        )

        assertTrue(
            buffer.add(
                Sample(
                    value =
                        3,

                    timestamp =
                        3L,
                )
            )
        )

        val snapshot =
            buffer.snapshot()

        assertEquals(
            2,
            snapshot.size,
        )

        assertEquals(
            2,
            snapshot[0].value,
        )

        assertEquals(
            3,
            snapshot[1].value,
        )
    }

    @Test
    fun snapshotDoesNotExposeMutableStorage() {
        val buffer =
            buffer(
                capacity =
                    4
            )

        buffer.add(
            Sample(
                value =
                    1,

                timestamp =
                    1L,
            )
        )

        val snapshot =
            buffer.snapshot()

        buffer.add(
            Sample(
                value =
                    2,

                timestamp =
                    2L,
            )
        )

        assertEquals(
            1,
            snapshot.size,
        )

        assertEquals(
            2,
            buffer.size,
        )
    }

    @Test
    fun clearRemovesAllSamples() {
        val buffer =
            buffer(
                capacity =
                    4
            )

        buffer.add(
            Sample(
                value =
                    1,

                timestamp =
                    1L,
            )
        )

        buffer.clear()

        assertEquals(
            0,
            buffer.size,
        )

        assertNull(
            buffer.latest()
        )
    }

    private fun buffer(
        capacity: Int,
    ) =
        NavigationTimedSampleBuffer<Sample>(
            capacity =
                capacity,

            timestampNanos = {
                it.timestamp
            },
        )
}