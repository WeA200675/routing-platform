package org.routingplatform.app.navigation

enum class NavigationRouteAcquisitionState {
    FallbackReady,
    LoadingInitial,
    LiveReady,
    Rerouting,
    Refreshing,
    LiveFailed,
    RerouteFailed,
}

data class NavigationRouteAcquisitionTelemetry(
    val state:
        NavigationRouteAcquisitionState,

    val message:
        String,

    val fault:
        NavigationFault? =
        null,
)

class NavigationRouteLifecycleController(
    private val bridge:
        JniNavigationCoreBridge,

    private val source:
        NavigationRouteSource,

    private val rerouteDecisionEngine:
        NavigationRerouteDecisionEngine =
        NavigationRerouteDecisionEngine(),
) : AutoCloseable {

    private var activeRequest:
        NavigationRouteRequest? =
        null

    private var activeHandle:
        NavigationRouteAcquisitionHandle? =
        null

    private var requestGeneration =
        0L

    private var rerouteInFlight =
        false

    private val trafficReevaluationPolicy =
        NavigationTrafficReevaluationPolicy()

    private val trafficReplacementPolicy =
        NavigationTrafficReplacementPolicy()

    private var activeRouteContract: NavigationRouteContract? = null

    private var latestTrustedRerouteOrigin: NavigationTrustedRefreshObservation? = null

    private var lastTrafficRefreshSuccessMs: Long? = null
    private var lastTrafficRefreshAttemptMs: Long? = null

    fun loadInitial(
        request:
            NavigationRouteRequest,

        snapshotProvider:
            () -> NavigationUiSnapshot,

        onSnapshot:
            (NavigationUiSnapshot) -> Unit,

        onTelemetry:
            (
                NavigationRouteAcquisitionTelemetry
            ) -> Unit,
    ) {
        cancelActiveRequest()

        activeRequest =
            request

        val generation =
            nextGeneration()

        onTelemetry(
            NavigationRouteAcquisitionTelemetry(
                state =
                    NavigationRouteAcquisitionState.LoadingInitial,

                message =
                    "Live-Route wird geladen",
            )
        )

        activeHandle =
            source.acquire(
                request
            ) {
                    result ->

                if (
                    generation !=
                        requestGeneration
                ) {
                    return@acquire
                }

                activeHandle =
                    null

                result.fold(
                    onSuccess = {
                            route ->

                        val current =
                            snapshotProvider()

                        if (
                            current.state !=
                                NavigationSessionState.Preview
                        ) {
                            onTelemetry(
                                NavigationRouteAcquisitionTelemetry(
                                    state =
                                        NavigationRouteAcquisitionState.FallbackReady,

                                    message =
                                        "Live-Antwort verworfen – aktive Session unverändert",
                                )
                            )

                            return@fold
                        }

                        val installed =
                            bridge.installRoute(
                                route
                            )

                        activeRouteContract = route

                        onSnapshot(
                            installed
                        )

                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state =
                                    NavigationRouteAcquisitionState.LiveReady,

                                message =
                                    "Live-Route aktiv",
                            )
                        )
                    },

                    onFailure = {
                            error ->

                        val fault =
                            NavigationReliabilityClassifier
                                .fromThrowable(
                                    error
                                )

                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state =
                                    NavigationRouteAcquisitionState.LiveFailed,

                                message =
                                    "Live-Route fehlgeschlagen – Fallback-Route aktiv: " +
                                        fault.userMessage,

                                fault =
                                    fault,
                            )
                        )
                    },
                )
            }
    }

    fun observeTelemetry(
        telemetry:
            NavigationRuntimeTelemetry,

        snapshotProvider:
            () -> NavigationUiSnapshot,

        onSnapshot:
            (NavigationUiSnapshot) -> Unit,

        onTelemetry:
            (
                NavigationRouteAcquisitionTelemetry
            ) -> Unit,
    ) {
        val decision =
            rerouteDecisionEngine
                .observe(
                    telemetry
                )

        NavigationTrustedRefreshOrigin
            .fromTelemetry(telemetry)
            ?.let { latestTrustedRerouteOrigin = it }

        if (
            decision !is
                NavigationRerouteDecision.RequestReplacement
        ) {
            return
        }

        if (
            rerouteInFlight ||
            activeHandle != null
        ) {
            return
        }

        val previousRequest =
            activeRequest
                ?: return

        /*
         * Automatic rerouting through unresolved via points is not
         * enabled yet because we cannot prove which via points are
         * still ahead of the vehicle.
         */
        if (
            previousRequest
                .viaPoints
                .isNotEmpty()
        ) {
            onTelemetry(
                NavigationRouteAcquisitionTelemetry(
                    state =
                        NavigationRouteAcquisitionState.RerouteFailed,

                    message =
                        "Automatisches Rerouting mit Via-Punkten nicht freigegeben",
                )
            )

            return
        }

        val current =
            snapshotProvider()

        if (
            current.state !=
                NavigationSessionState.Navigating
        ) {
            return
        }

        val expectedSessionId =
            current.sessionId

        val replacementRequest =
            previousRequest.copy(
                origin =
                    decision.origin,

                viaPoints =
                    emptyList(),
            )

        rerouteInFlight =
            true

        val generation =
            nextGeneration()

        onTelemetry(
            NavigationRouteAcquisitionTelemetry(
                state =
                    NavigationRouteAcquisitionState.Rerouting,

                message =
                    "Rerouting wird geprüft",
            )
        )

        activeHandle =
            source.acquire(
                replacementRequest
            ) {
                    result ->

                if (
                    generation !=
                        requestGeneration
                ) {
                    rerouteInFlight =
                        false

                    return@acquire
                }

                activeHandle =
                    null

                val latest =
                    snapshotProvider()

                if (
                    latest.state !=
                        NavigationSessionState.Navigating ||
                    latest.sessionId !=
                        expectedSessionId
                ) {
                    rerouteInFlight =
                        false

                    onTelemetry(
                        NavigationRouteAcquisitionTelemetry(
                            state =
                                NavigationRouteAcquisitionState.LiveReady,

                            message =
                                "Veraltete Reroute-Antwort verworfen",
                        )
                    )

                    return@acquire
                }

                result.fold(
                    onSuccess = {
                            route ->

                        /*
                         * Explicit session replacement boundary.
                         *
                         * Normal installRoute() still cannot mutate
                         * a Navigating session.
                         */
                        val replacement =
                            bridge
                                .replaceNavigatingRoute(
                                    route
                                )

                        activeRequest =
                            replacementRequest

                        activeRouteContract = route

                        rerouteDecisionEngine
                            .onRouteReplaced()

                        rerouteInFlight =
                            false

                        onSnapshot(
                            replacement
                        )

                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state =
                                    NavigationRouteAcquisitionState.LiveReady,

                                message =
                                    "Live-Route nach Rerouting aktiv",
                            )
                        )
                    },

                    onFailure = {
                            error ->

                        rerouteInFlight =
                            false

                        val fault =
                            NavigationReliabilityClassifier
                                .fromThrowable(
                                    error
                                )

                        /*
                         * Fail safely: acquisition failure never
                         * removes or mutates the active old route.
                         */
                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state =
                                    NavigationRouteAcquisitionState.RerouteFailed,

                                message =
                                    "Rerouting fehlgeschlagen – alte Route bleibt aktiv: " +
                                        fault.userMessage,

                                fault =
                                    fault,
                            )
                        )
                    },
                )
            }
    }

    fun reevaluateActiveRoute(
        nowMs: Long,
        snapshotProvider: () -> NavigationUiSnapshot,
        onSnapshot: (NavigationUiSnapshot) -> Unit,
        onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
    ) {
        val request = activeRequest ?: return
        val current = snapshotProvider()
        if (current.state != NavigationSessionState.Navigating) return

        /*
         * A periodic refresh cannot safely reuse the trip's original origin:
         * the vehicle may already be far along the route. With via points we
         * additionally cannot prove which stops have already been visited.
         * Until trusted current-origin/remaining-via tracking is available,
         * fail closed rather than installing a route for the wrong trip leg.
         */
        if (request.viaPoints.isNotEmpty()) {
            onTelemetry(
                NavigationRouteAcquisitionTelemetry(
                    state = NavigationRouteAcquisitionState.LiveReady,
                    message = "Routenprüfung mit offenen Via-Punkten aus Sicherheitsgründen ausgesetzt",
                )
            )
            return
        }

        // A moving-origin candidate is not comparable with the active route's
        // original-trip duration. Until the provider exposes an exact remaining-route
        // baseline with matching provenance, periodic replacement must fail closed.
        onTelemetry(
            NavigationRouteAcquisitionTelemetry(
                state = NavigationRouteAcquisitionState.LiveReady,
                message = "Routenprüfung ausgesetzt: keine exakt vergleichbare Reststreckenmetrik",
            )
        )
        return

    fun resetRerouteEvidence() {
        rerouteDecisionEngine.reset()
    }

    override fun close() {
        cancelActiveRequest()

        rerouteDecisionEngine.reset()

        source.close()
    }

    private fun cancelActiveRequest() {
        activeHandle
            ?.cancel()

        activeHandle =
            null

        rerouteInFlight =
            false

        lastTrafficRefreshSuccessMs = null
        lastTrafficRefreshAttemptMs = null
        activeRouteContract = null
        latestTrustedRerouteOrigin = null

        requestGeneration +=
            1L
    }

    private fun nextGeneration():
        Long {

        requestGeneration +=
            1L

        return requestGeneration
    }
}