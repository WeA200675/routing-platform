package org.routingplatform.app.navigation

import kotlin.math.max

enum class NavigationRouteProgressSafetyStatus {
    Accepted,
    HeldAmbiguous,
    HeldLowConfidence,
    HeldOffRoute,
    HeldBackward,
    HeldForwardJump,
    HeldNoEstimate,
}

data class NavigationRouteProgressSafetyDecision(
    val status:
        NavigationRouteProgressSafetyStatus,

    val acceptedProgress:
        RouteProgressAnchor?,

    val hypothesis:
        NavigationRouteHypothesis?,

    val ambiguityMargin:
        Double?,
) {
    val mayUpdateNativeRuntime: Boolean
        get() =
            status ==
                NavigationRouteProgressSafetyStatus.Accepted &&
                acceptedProgress !=
                    null
}

data class NavigationRouteProgressSafetyPolicy(
    val maximumDistanceToRouteM:
        Double =
        60.0,

    val minimumAmbiguityMargin:
        Double =
        0.75,

    val backwardToleranceM:
        Double =
        4.0,

    val minimumForwardAllowanceM:
        Double =
        30.0,

    val forwardSpeedMultiplier:
        Double =
        3.0,

    val uncertaintyAllowanceMultiplier:
        Double =
        2.0,
) {
    init {
        require(
            maximumDistanceToRouteM > 0.0
        )

        require(
            minimumAmbiguityMargin >= 0.0
        )

        require(
            backwardToleranceM >= 0.0
        )

        require(
            minimumForwardAllowanceM > 0.0
        )

        require(
            forwardSpeedMultiplier > 0.0
        )

        require(
            uncertaintyAllowanceMultiplier >= 0.0
        )
    }
}

class NavigationRouteProgressSafetyGate(
    private val matcher:
        NavigationMultiHypothesisRouteMatcher =
        NavigationMultiHypothesisRouteMatcher(),

    private val policy:
        NavigationRouteProgressSafetyPolicy =
        NavigationRouteProgressSafetyPolicy(),
) {
    private var lastAccepted:
        RouteProgressAnchor? =
        null

    private var lastAcceptedElapsedRealtimeNanos:
        Long? =
        null

    fun evaluate(
        route:
            List<RoutePoint>,

        fusion:
            NavigationPositionFusionUpdate,
    ): NavigationRouteProgressSafetyDecision {

        val estimate =
            fusion.estimate
                ?: return held(
                    NavigationRouteProgressSafetyStatus.HeldNoEstimate
                )

        if (
            estimate.confidence !=
                NavigationPositionConfidence.High &&
            estimate.confidence !=
                NavigationPositionConfidence.Medium
        ) {
            return held(
                NavigationRouteProgressSafetyStatus.HeldLowConfidence
            )
        }

        val match =
            matcher.match(
                route =
                    route,

                estimate =
                    estimate,

                previousProgress =
                    lastAccepted,
            )

        val best =
            match.best
                ?: return held(
                    NavigationRouteProgressSafetyStatus.HeldOffRoute
                )

        if (
            best.distanceToRouteM >
                policy.maximumDistanceToRouteM
        ) {
            return held(
                status =
                    NavigationRouteProgressSafetyStatus.HeldOffRoute,

                hypothesis =
                    best,

                ambiguityMargin =
                    match.ambiguityMargin,
            )
        }

        val ambiguityMargin =
            match.ambiguityMargin

        if (
            ambiguityMargin != null &&
            ambiguityMargin <
                policy.minimumAmbiguityMargin
        ) {
            return held(
                status =
                    NavigationRouteProgressSafetyStatus.HeldAmbiguous,

                hypothesis =
                    best,

                ambiguityMargin =
                    ambiguityMargin,
            )
        }

        val previous =
            lastAccepted

        if (
            previous != null
        ) {
            val previousDistance =
                alongRouteDistanceMeters(
                    route =
                        route,

                    anchor =
                        previous,
                )

            val candidateDistance =
                best.alongRouteDistanceM

            val deltaM =
                candidateDistance -
                    previousDistance

            if (
                deltaM <
                    -policy.backwardToleranceM
            ) {
                return held(
                    status =
                        NavigationRouteProgressSafetyStatus.HeldBackward,

                    hypothesis =
                        best,

                    ambiguityMargin =
                        ambiguityMargin,
                )
            }

            val previousTimestamp =
                lastAcceptedElapsedRealtimeNanos

            if (
                previousTimestamp != null &&
                estimate.elapsedRealtimeNanos >
                    previousTimestamp
            ) {
                val elapsedSeconds =
                    (
                        estimate.elapsedRealtimeNanos -
                            previousTimestamp
                    ).toDouble() /
                        NANOS_PER_SECOND

                val speed =
                    estimate
                        .horizontalVelocityMps
                        ?: 0.0

                val uncertainty =
                    estimate
                        .covariance
                        .conservativeHorizontalSigmaM

                val maximumForwardM =
                    max(
                        policy.minimumForwardAllowanceM,
                        speed *
                            elapsedSeconds *
                            policy.forwardSpeedMultiplier +
                            uncertainty *
                                policy.uncertaintyAllowanceMultiplier,
                    )

                if (
                    deltaM >
                        maximumForwardM
                ) {
                    return held(
                        status =
                            NavigationRouteProgressSafetyStatus.HeldForwardJump,

                        hypothesis =
                            best,

                        ambiguityMargin =
                            ambiguityMargin,
                    )
                }
            }
        }

        val acceptedAnchor =
            if (
                previous != null &&
                best.alongRouteDistanceM <
                    alongRouteDistanceMeters(
                        route =
                            route,

                        anchor =
                            previous,
                    )
            ) {
                previous
            } else {
                best.anchor
            }

        lastAccepted =
            acceptedAnchor

        lastAcceptedElapsedRealtimeNanos =
            estimate.elapsedRealtimeNanos

        return NavigationRouteProgressSafetyDecision(
            status =
                NavigationRouteProgressSafetyStatus.Accepted,

            acceptedProgress =
                acceptedAnchor,

            hypothesis =
                best,

            ambiguityMargin =
                ambiguityMargin,
        )
    }

    fun seed(
        progress: RouteProgressAnchor,
    ) {
        lastAccepted =
            progress

        lastAcceptedElapsedRealtimeNanos =
            null
    }

    fun currentAcceptedProgress():
        RouteProgressAnchor? =
        lastAccepted

    fun reset() {
        lastAccepted =
            null

        lastAcceptedElapsedRealtimeNanos =
            null
    }

    private fun held(
        status:
            NavigationRouteProgressSafetyStatus,

        hypothesis:
            NavigationRouteHypothesis? =
            null,

        ambiguityMargin:
            Double? =
            null,
    ) =
        NavigationRouteProgressSafetyDecision(
            status =
                status,

            acceptedProgress =
                lastAccepted,

            hypothesis =
                hypothesis,

            ambiguityMargin =
                ambiguityMargin,
        )
}

private const val NANOS_PER_SECOND =
    1_000_000_000.0