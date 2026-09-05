package org.routingplatform.app.navigation

data class NavigationReroutePolicy(
    val minimumEvidenceDurationNanos:
        Long =
        10_000_000_000L,

    val minimumConsecutiveSamples:
        Int =
        6,

    val minimumAttemptIntervalNanos:
        Long =
        30_000_000_000L,
) {
    init {
        require(
            minimumEvidenceDurationNanos >
                0L
        )

        require(
            minimumConsecutiveSamples >=
                2
        )

        require(
            minimumAttemptIntervalNanos >=
                0L
        )
    }
}

sealed class NavigationRerouteDecision {
    object Hold :
        NavigationRerouteDecision()

    data class RequestReplacement(
        val origin: RoutePoint,
        val observedAtElapsedRealtimeNanos:
            Long,
    ) : NavigationRerouteDecision()
}

class NavigationRerouteDecisionEngine(
    private val policy:
        NavigationReroutePolicy =
        NavigationReroutePolicy(),
) {
    private var firstEligibleNanos:
        Long? =
        null

    private var lastObservedNanos:
        Long? =
        null

    private var eligibleSamples =
        0

    private var lastAttemptNanos:
        Long? =
        null

    fun observe(
        telemetry:
            NavigationRuntimeTelemetry,
    ): NavigationRerouteDecision {

        val timestamp =
            telemetry
                .lastLocationElapsedRealtimeNanos
                ?: return resetAndHold()

        val position =
            telemetry
                .lastObservedPosition
                ?: return resetAndHold()

        val previousTimestamp =
            lastObservedNanos

        if (
            previousTimestamp !=
                null &&
            timestamp <=
                previousTimestamp
        ) {
            return resetAndHold()
        }

        lastObservedNanos =
            timestamp

        /*
         * Only a route mismatch with otherwise trusted positioning
         * evidence can ever become a reroute request.
         *
         * Ambiguity, low confidence, dead reckoning, forward jumps
         * and missing estimates remain HOLD states.
         */
        val eligible =
            telemetry.safetyStatus ==
                NavigationRouteProgressSafetyStatus.HeldOffRoute &&
                telemetry.confidence in
                    TRUSTED_REROUTE_CONFIDENCE &&
                telemetry.fusionMode !=
                    NavigationFusionMode.DeadReckoning

        if (
            !eligible
        ) {
            resetCandidate()
            return NavigationRerouteDecision.Hold
        }

        val first =
            firstEligibleNanos

        if (
            first ==
                null
        ) {
            firstEligibleNanos =
                timestamp

            eligibleSamples =
                1

            return NavigationRerouteDecision.Hold
        }

        eligibleSamples +=
            1

        val evidenceDuration =
            timestamp -
                first

        if (
            evidenceDuration <
                policy
                    .minimumEvidenceDurationNanos
        ) {
            return NavigationRerouteDecision.Hold
        }

        if (
            eligibleSamples <
                policy
                    .minimumConsecutiveSamples
        ) {
            return NavigationRerouteDecision.Hold
        }

        val previousAttempt =
            lastAttemptNanos

        if (
            previousAttempt !=
                null &&
            timestamp -
                previousAttempt <
                policy
                    .minimumAttemptIntervalNanos
        ) {
            return NavigationRerouteDecision.Hold
        }

        lastAttemptNanos =
            timestamp

        resetCandidate()

        return NavigationRerouteDecision
            .RequestReplacement(
                origin =
                    position,

                observedAtElapsedRealtimeNanos =
                    timestamp,
            )
    }

    fun onRouteReplaced() {
        resetCandidate()
    }

    fun reset() {
        firstEligibleNanos =
            null

        lastObservedNanos =
            null

        eligibleSamples =
            0

        lastAttemptNanos =
            null
    }

    private fun resetAndHold():
        NavigationRerouteDecision {

        resetCandidate()

        return NavigationRerouteDecision.Hold
    }

    private fun resetCandidate() {
        firstEligibleNanos =
            null

        eligibleSamples =
            0
    }
}

private val TRUSTED_REROUTE_CONFIDENCE =
    setOf(
        NavigationPositionConfidence.High,
        NavigationPositionConfidence.Medium,
    )