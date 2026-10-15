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
    private var retryHandle: android.os.Handler? = null
    private var retryToken = 0L

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
        scheduleTokenBump()
        loadInitialAttempt(request, snapshotProvider, onSnapshot, onTelemetry, retryNumber = 0)
    }

    private fun loadInitialAttempt(
        request: NavigationRouteRequest,
        snapshotProvider: () -> NavigationUiSnapshot,
        onSnapshot: (NavigationUiSnapshot) -> Unit,
        onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
        retryNumber: Int,
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

                        val mayRetry = fault.disposition == NavigationFaultDisposition.RetryableInfrastructure &&
                            retryNumber == 0 && snapshotProvider().state == NavigationSessionState.Preview
                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state =
                                    NavigationRouteAcquisitionState.LiveFailed,

                                message =
                                    if (mayRetry) "Live-Route nicht erreichbar – erneuter Versuch läuft: ${fault.userMessage}"
                                    else "Live-Route fehlgeschlagen – Fallback-Route aktiv: ${fault.userMessage}",

                                fault =
                                    fault,
                            )
                        )
                        if (mayRetry) {
                            val token = retryToken
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            retryHandle = handler
                            handler.postDelayed({
                                if (token == retryToken && snapshotProvider().state == NavigationSessionState.Preview) {
                                    loadInitialAttempt(request, snapshotProvider, onSnapshot, onTelemetry, retryNumber = 1)
                                }
                            }, INITIAL_RETRY_DELAY_MS)
                        }
                    },
                )
            }
    }

    /**
     * Replaces the active route only after a complete replacement has been
     * acquired and the same navigation session is still active. A failed
     * lookup leaves the current route and guidance untouched.
     */
    fun submitRouteForCurrentSession(
        request: NavigationRouteRequest,
        snapshotProvider: () -> NavigationUiSnapshot,
        onSnapshot: (NavigationUiSnapshot) -> Unit,
        onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
        maximumExtraDurationSeconds: Double? = null,
    ) {
        when (snapshotProvider().state) {
            NavigationSessionState.Preview -> loadInitial(
                request = request,
                snapshotProvider = snapshotProvider,
                onSnapshot = onSnapshot,
                onTelemetry = onTelemetry,
            )
            NavigationSessionState.Navigating -> replaceNavigatingRoute(
                request = request,
                snapshotProvider = snapshotProvider,
                onSnapshot = onSnapshot,
                onTelemetry = onTelemetry,
                maximumExtraDurationSeconds = maximumExtraDurationSeconds,
            )
            else -> onTelemetry(
                NavigationRouteAcquisitionTelemetry(
                    state = NavigationRouteAcquisitionState.RerouteFailed,
                    message = "Route kann im aktuellen Navigationsstatus nicht geändert werden.",
                )
            )
        }
    }

    fun replaceNavigatingRoute(
        request: NavigationRouteRequest,
        snapshotProvider: () -> NavigationUiSnapshot,
        onSnapshot: (NavigationUiSnapshot) -> Unit,
        onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
        maximumExtraDurationSeconds: Double? = null,
    ) {
        require(maximumExtraDurationSeconds == null ||
            (maximumExtraDurationSeconds.isFinite() && maximumExtraDurationSeconds >= 0.0)
        ) { "Maximum route detour must be finite and non-negative." }
        val initial = snapshotProvider()
        if (initial.state != NavigationSessionState.Navigating) {
            onTelemetry(
                NavigationRouteAcquisitionTelemetry(
                    state = NavigationRouteAcquisitionState.RerouteFailed,
                    message = "Zwischenziel abgebrochen – Navigation ist nicht aktiv.",
                )
            )
            return
        }

        cancelActiveRequest()
        val generation = nextGeneration()
        val sessionId = initial.sessionId
        rerouteInFlight = true
        onTelemetry(
            NavigationRouteAcquisitionTelemetry(
                state = NavigationRouteAcquisitionState.Rerouting,
                message = "Zwischenziel wird geprüft – aktuelle Route bleibt aktiv.",
            )
        )

        activeHandle = source.acquire(request) { result ->
            if (generation != requestGeneration) return@acquire
            activeHandle = null

            val current = snapshotProvider()
            if (current.state != NavigationSessionState.Navigating ||
                current.sessionId != sessionId
            ) {
                rerouteInFlight = false
                onTelemetry(
                    NavigationRouteAcquisitionTelemetry(
                        state = NavigationRouteAcquisitionState.RerouteFailed,
                        message = "Neue Route verworfen – Navigation hat sich inzwischen geändert.",
                    )
                )
                return@acquire
            }

            result.fold(
                onSuccess = { route ->
                    val latest = snapshotProvider()
                    if (maximumExtraDurationSeconds != null) {
                        val extraDuration = (route.durationS - latest.remainingDurationS)
                            .coerceAtLeast(0.0)
                        if (extraDuration > maximumExtraDurationSeconds) {
                            rerouteInFlight = false
                            val extraMinutes = kotlin.math.ceil(extraDuration / 60.0).toInt()
                            val allowedMinutes = kotlin.math.floor(maximumExtraDurationSeconds / 60.0).toInt()
                            onTelemetry(
                                NavigationRouteAcquisitionTelemetry(
                                    state = NavigationRouteAcquisitionState.RerouteFailed,
                                    message = "Der berechnete Umweg beträgt etwa $extraMinutes Minuten und überschreitet dein Limit von $allowedMinutes Minuten. Die bisherige Route bleibt aktiv.",
                                )
                            )
                            return@fold
                        }
                    }
                    runCatching {
                        bridge.replaceNavigatingRoute(route)
                    }.onSuccess { updated ->
                        activeRouteContract = route
                        activeRequest = request
                        rerouteDecisionEngine.reset()
                        rerouteInFlight = false
                        onSnapshot(updated)
                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state = NavigationRouteAcquisitionState.LiveReady,
                                message = "Zwischenziel übernommen – Navigation läuft auf der aktualisierten Route weiter.",
                            )
                        )
                    }.onFailure { error ->
                        rerouteInFlight = false
                        val fault = NavigationReliabilityClassifier.fromThrowable(error)
                        onTelemetry(
                            NavigationRouteAcquisitionTelemetry(
                                state = NavigationRouteAcquisitionState.RerouteFailed,
                                message = "Neue Route konnte nicht sicher übernommen werden. Die bisherige Navigation bleibt aktiv: " +
                                    fault.userMessage,
                                fault = fault,
                            )
                        )
                    }
                },
                onFailure = { error ->
                    rerouteInFlight = false
                    val fault = NavigationReliabilityClassifier.fromThrowable(error)
                    onTelemetry(
                        NavigationRouteAcquisitionTelemetry(
                            state = NavigationRouteAcquisitionState.RerouteFailed,
                            message = "Keine sichere Route zum Zwischenziel gefunden. Die bisherige Navigation bleibt aktiv: " +
                                fault.userMessage,
                            fault = fault,
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
        val current = snapshotProvider()
        if (current.state != NavigationSessionState.Navigating) {
            rerouteDecisionEngine.reset()
            return
        }

        val decision =
            rerouteDecisionEngine
                .observe(
                    telemetry = telemetry,
                    sessionId = current.sessionId,
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
        require(nowMs >= 0L)
        val current = snapshotProvider()
        if (current.state != NavigationSessionState.Navigating) return

        /*
         * The configured route endpoint returns route geometry and maneuvers,
         * not timestamped traffic events or an exactly comparable remaining-time
         * baseline. Keep the active route stable and report that limitation.
         * TrafficDetourOfferPolicy admits a proposal only from a future, validated
         * live-feed integration with a current delay estimate and alternate route.
         */
        onTelemetry(
            NavigationRouteAcquisitionTelemetry(
                state = NavigationRouteAcquisitionState.LiveReady,
                message = "Live-Verkehrsfeed nicht verbunden – aktuelle Route bleibt aktiv",
            )
        )
    }

    fun resetRerouteEvidence() {
        rerouteDecisionEngine.reset()
    }

    override fun close() {
        cancelScheduledRetry()
        cancelActiveRequest()

        rerouteDecisionEngine.reset()

        source.close()
    }

    private fun cancelActiveRequest() {
        cancelScheduledRetry()
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

    private fun scheduleTokenBump() {
        retryToken += 1L
        cancelScheduledRetry()
    }

    private fun cancelScheduledRetry() {
        retryHandle?.removeCallbacksAndMessages(null)
        retryHandle = null
    }

    private companion object {
        const val INITIAL_RETRY_DELAY_MS = 1_500L
    }
}
