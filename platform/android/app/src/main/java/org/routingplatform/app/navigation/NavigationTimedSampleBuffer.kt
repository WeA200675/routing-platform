package org.routingplatform.app.navigation

class NavigationTimedSampleBuffer<T>(
    private val capacity: Int,
    private val timestampNanos:
        (T) -> Long,
) {
    init {
        require(
            capacity > 0
        ) {
            "Sample buffer capacity must be positive."
        }
    }

    private val samples =
        ArrayDeque<T>()

    fun add(
        sample: T,
    ): Boolean {

        val timestamp =
            timestampNanos(
                sample
            )

        require(
            timestamp >= 0L
        ) {
            "Sample timestamp must not be negative."
        }

        val latestTimestamp =
            samples
                .lastOrNull()
                ?.let(
                    timestampNanos
                )

        if (
            latestTimestamp != null &&
            timestamp <=
                latestTimestamp
        ) {
            return false
        }

        samples.addLast(
            sample
        )

        while (
            samples.size >
                capacity
        ) {
            samples.removeFirst()
        }

        return true
    }

    fun latest():
        T? =
        samples.lastOrNull()

    fun snapshot():
        List<T> =
        samples.toList()

    val size: Int
        get() =
            samples.size

    fun clear() {
        samples.clear()
    }
}