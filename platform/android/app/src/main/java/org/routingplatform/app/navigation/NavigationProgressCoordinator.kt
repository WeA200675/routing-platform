package org.routingplatform.app.navigation

data class NavigationProgressCoordinatorResult(
    val fusionUpdate:
        NavigationPositionFusionUpdate,

    val safetyDecision:
        NavigationRouteProgressSafetyDecision,

    val nativeSnapshot:
        NavigationUiSnapshot?,

    val nativeUpdateAttempted:
        Boolean,

    val nativeForwarded:
        Boolean,

    val nativeFailureClass:
        String?,

    val nativeFailureMessage:
        String?,
)

class NavigationNativeProgressArbiter(
    private val minimumForwardDelta:
        Double =
        1.0e-7,
) {
    init {
        require(
            minimumForwardDelta >= 0.0 &&
                minimumForwardDelta.isFinite()
        )
    }

    private var lastForwarded:
        RouteProgressAnchor? =
        null

    fun seed(
        anchor:
            RouteProgressAnchor,
    ) {
        lastForwarded =
            anchor
    }

    fun shouldForward(
        candidate:
            RouteProgressAnchor,
    ): Boolean {

        val previous =
            lastForwarded
                ?: return true

        return candidate.shapePosition >
            previous.shapePosition +
                minimumForwardDelta
    }

    fun markForwarded(
        candidate:
            RouteProgressAnchor,
    ) {
        require(
            shouldForward(
                candidate
            )
        ) {
            "Native progress must move strictly forward."
        }

        lastForwarded =
            candidate
    }

    fun current():
        RouteProgressAnchor? =
        lastForwarded

    fun reset() {
        lastForwarded =
            null
    }
}

class NavigationProgressCoordinator(
    private val bridge:
        NavigationCoreBridge,

    private val fusionEngine:
        NavigationPositionFusionEngine =
        NavigationPositionFusionEngine(),

    private val safetyGate:
        NavigationRouteProgressSafetyGate =
        NavigationRouteProgressSafetyGate(),

    private val nativeArbiter:
        NavigationNativeProgressArbiter =
        NavigationNativeProgressArbiter(),
) {
    fun start(
        initialProgress:
            RouteProgressAnchor,
    ) {
        fusionEngine.reset()

        safetyGate.reset()

        nativeArbiter.reset()

        safetyGate.seed(
            initialProgress
        )

        nativeArbiter.seed(
            initialProgress
        )
    }

    fun ingest(
        route:
            List<RoutePoint>,

        sample:
            NavigationLocationSample,

        sensors:
            NavigationSensorEvidenceSnapshot,

        environment:
            NavigationEnvironmentEvidenceSnapshot?,

        rawGnss:
            NavigationRawGnssSnapshot?,
    ): NavigationProgressCoordinatorResult {

        val fusion =
            fusionEngine.ingest(
                sample =
                    sample,

                sensors =
                    sensors,

                environment =
                    environment,

                rawGnss =
                    rawGnss,
            )

        val safety =
            safetyGate.evaluate(
                route =
                    route,

                fusion =
                    fusion,
            )

        val candidate =
            safety.acceptedProgress

        if (
            !safety.mayUpdateNativeRuntime ||
            candidate ==
                null ||
            !nativeArbiter.shouldForward(
                candidate
            )
        ) {
            return NavigationProgressCoordinatorResult(
                fusionUpdate =
                    fusion,

                safetyDecision =
                    safety,

                nativeSnapshot =
                    null,

                nativeUpdateAttempted =
                    false,

                nativeForwarded =
                    false,

                nativeFailureClass =
                    null,

                nativeFailureMessage =
                    null,
            )
        }

        val nativeResult =
            runCatching {
                bridge.updateProgress(
                    shapeSegmentIndex =
                        candidate
                            .shapeSegmentIndex,

                    segmentFraction =
                        candidate
                            .segmentFraction,
                )
            }

        val nativeSnapshot =
            nativeResult
                .getOrNull()

        val nativeFailure =
            nativeResult
                .exceptionOrNull()

        if (
            nativeSnapshot !=
                null
        ) {
            /*
             * We only advance the arbiter AFTER JNI accepted
             * the progress update.
             *
             * A native exception therefore cannot silently
             * advance our local safety state.
             */
            nativeArbiter.markForwarded(
                candidate
            )
        }

        return NavigationProgressCoordinatorResult(
            fusionUpdate =
                fusion,

            safetyDecision =
                safety,

            nativeSnapshot =
                nativeSnapshot,

            nativeUpdateAttempted =
                true,

            nativeForwarded =
                nativeSnapshot !=
                    null,

            nativeFailureClass =
                nativeFailure
                    ?.javaClass
                    ?.name,

            nativeFailureMessage =
                nativeFailure
                    ?.let {
                        it.message
                            ?: it.javaClass.name
                    },
        )
    }

    fun currentNativeProgress():
        RouteProgressAnchor? =
        nativeArbiter.current()

    fun currentPosition():
        NavigationPositionEstimate? =
        fusionEngine.currentEstimate()

    fun reset() {
        fusionEngine.reset()
        safetyGate.reset()
        nativeArbiter.reset()
    }
}