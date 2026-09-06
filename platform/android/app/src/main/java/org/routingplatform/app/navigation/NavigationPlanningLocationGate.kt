package org.routingplatform.app.navigation

data class NavigationPlanningLocation(
    val position:
        RoutePoint,

    val horizontalAccuracyM:
        Double,
)

internal class NavigationPlanningLocationGate(
    private val maxHorizontalAccuracyM:
        Double =
        DEFAULT_MAX_PLANNING_ACCURACY_M,

    private val maxSampleAgeNanos:
        Long =
        DEFAULT_MAX_PLANNING_SAMPLE_AGE_NANOS,
) {
    init {
        require(
            maxHorizontalAccuracyM.isFinite() &&
                maxHorizontalAccuracyM >=
                0.0
        )

        require(
            maxSampleAgeNanos >=
                0L
        )
    }

    fun accept(
        sample:
            NavigationLocationSample,

        nowElapsedRealtimeNanos:
            Long,
    ): NavigationPlanningLocation? {

        require(
            nowElapsedRealtimeNanos >=
                0L
        )

        val accuracy =
            sample.horizontalAccuracyM
                ?: return null

        if (
            !accuracy.isFinite() ||
            accuracy >
                maxHorizontalAccuracyM
        ) {
            return null
        }

        val ageNanos =
            nowElapsedRealtimeNanos -
                sample.elapsedRealtimeNanos

        if (
            ageNanos <
                0L ||
            ageNanos >
                maxSampleAgeNanos
        ) {
            return null
        }

        return NavigationPlanningLocation(
            position =
                sample.position,

            horizontalAccuracyM =
                accuracy,
        )
    }
}

/*
 * Monotonic generation gate for asynchronous one-shot work.
 *
 * A callback may consume only the generation that is still current.
 * Starting or cancelling newer work permanently invalidates older
 * callbacks; stale results cannot become navigation/planning truth.
 */
internal class NavigationAsyncGenerationGate {

    private var generation =
        0L

    @Synchronized
    fun begin():
        Long {

        generation +=
            1L

        return generation
    }

    @Synchronized
    fun isCurrent(
        token:
            Long,
    ): Boolean =
        token ==
            generation

    @Synchronized
    fun consume(
        token:
            Long,
    ): Boolean {

        if (
            token !=
                generation
        ) {
            return false
        }

        generation +=
            1L

        return true
    }

    @Synchronized
    fun cancel() {
        generation +=
            1L
    }
}

private const val DEFAULT_MAX_PLANNING_ACCURACY_M =
    100.0

private const val DEFAULT_MAX_PLANNING_SAMPLE_AGE_NANOS =
    20_000_000_000L