package org.routingplatform.app.navigation

class NavigationObservationBuffer(
    private val capacity: Int = 128,
) {
    init {
        require(
            capacity > 0
        ) {
            "Observation buffer capacity must be positive."
        }
    }

    private val observations =
        mutableListOf<
            NavigationPositionObservation
        >()

    fun add(
        observation: NavigationPositionObservation,
    ): Boolean {

        val latest =
            observations.lastOrNull()

        if (
            latest != null &&
            observation.elapsedRealtimeNanos <=
                latest.elapsedRealtimeNanos
        ) {
            return false
        }

        observations.add(
            observation
        )

        while (
            observations.size >
                capacity
        ) {
            observations.removeAt(
                0
            )
        }

        return true
    }

    fun latest():
        NavigationPositionObservation? =
        observations.lastOrNull()

    fun snapshot():
        List<NavigationPositionObservation> =
        observations.toList()

    val size: Int
        get() =
            observations.size

    fun clear() {
        observations.clear()
    }
}