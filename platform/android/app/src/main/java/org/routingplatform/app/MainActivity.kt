package org.routingplatform.app

import android.Manifest
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.maplibre.android.MapLibre
import org.routingplatform.app.navigation.AndroidNavigationPlanningLocationController
import org.routingplatform.app.navigation.AndroidNavigationRuntimeController
import org.routingplatform.app.navigation.JniNavigationCoreBridge
import org.routingplatform.app.navigation.NavigationDriveProofObservationSinkFactory
import org.routingplatform.app.navigation.NavigationExitLookaheadEngine
import org.routingplatform.app.navigation.NavigationRouteAcquisitionState
import org.routingplatform.app.navigation.NavigationRouteAcquisitionTelemetry
import org.routingplatform.app.navigation.NavigationRouteBootstrap
import org.routingplatform.app.navigation.NavigationRouteIntentRequest
import org.routingplatform.app.navigation.NavigationRouteLifecycleController
import org.routingplatform.app.navigation.NavigationRouteProgressSafetyStatus
import org.routingplatform.app.navigation.NavigationRouteSourceFactory
import org.routingplatform.app.navigation.NavigationRuntimeTelemetry
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationStartOrientationEngine
import org.routingplatform.app.navigation.NavigationStartOrientationVisibilityController
import org.routingplatform.app.navigation.NavigationTripPlan
import org.routingplatform.app.navigation.NavigationTripStop
import org.routingplatform.app.navigation.RouteProgressAnchor
import org.routingplatform.app.navigation.alongRouteDistanceMeters
import org.routingplatform.app.navigation.buildDiagnosticProgressAnchors
import org.routingplatform.app.navigation.buildNavigationRouteEvents
import org.routingplatform.app.navigation.buildNavigationStartRoadContext
import org.routingplatform.app.navigation.hasNavigationLocationPermission
import org.routingplatform.app.navigation.hasPreciseNavigationLocationPermission
import org.routingplatform.app.navigation.navigationRuntimePermissionsToRequest
import org.routingplatform.app.places.AndroidFavoriteDestinationStore
import org.routingplatform.app.places.AndroidGeocoderDestinationSearchSource
import org.routingplatform.app.places.DestinationSearchHandle
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestinationCollection
import org.routingplatform.app.profile.AndroidUserProfileStore
import org.routingplatform.app.profile.ExperiencePackSelectionSource
import org.routingplatform.app.ui.NavigationAssistOverlay
import org.routingplatform.app.ui.NavigationObservedPositionPresentation
import org.routingplatform.app.ui.NavigationScreen
import org.routingplatform.app.ui.RoutingPlatformTheme

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState
        )

        MapLibre.getInstance(
            this
        )

        setContent {
            val bridge =
                remember {
                    JniNavigationCoreBridge()
                }

            val profileStore =
                remember {
                    AndroidUserProfileStore(
                        applicationContext
                    )
                }

            var activeProfile by
                remember {
                    mutableStateOf(
                        profileStore
                            .loadActiveProfile()
                    )
                }

            val favoriteDestinationStore =
                remember {
                    AndroidFavoriteDestinationStore(
                        applicationContext
                    )
                }

            var favoriteDestinations by
                remember(
                    activeProfile.profileId
                ) {
                    mutableStateOf(
                        favoriteDestinationStore
                            .load(
                                activeProfile.profileId
                            )
                    )
                }

            val destinationSearchSource =
                remember {
                    AndroidGeocoderDestinationSearchSource(
                        applicationContext
                    )
                }

            var destinationSearchHandle by
                remember {
                    mutableStateOf<
                        DestinationSearchHandle?
                    >(
                        null
                    )
                }

            var destinationSearchResults by
                remember {
                    mutableStateOf(
                        emptyList<
                            DestinationSearchResult
                        >()
                    )
                }

            var destinationPlannerMessage by
                remember {
                    mutableStateOf<String?>(
                        null
                    )
                }

            var destinationPlannerBusy by
                remember {
                    mutableStateOf(
                        false
                    )
                }

            val planningLocationController =
                remember {
                    AndroidNavigationPlanningLocationController(
                        applicationContext
                    )
                }

            DisposableEffect(
                destinationSearchSource,
                planningLocationController,
            ) {
                onDispose {
                    destinationSearchSource
                        .close()

                    planningLocationController
                        .close()
                }
            }

            val runtimeController =
                remember {
                    AndroidNavigationRuntimeController(
                        context =
                            applicationContext,

                        bridge =
                            bridge,
                    )
                }

            val driveProofObservationSink =
                remember {
                    NavigationDriveProofObservationSinkFactory
                        .create(
                            applicationContext
                        )
                }

            DisposableEffect(
                driveProofObservationSink
            ) {
                onDispose {
                    driveProofObservationSink
                        .close()
                }
            }

            val startOrientationEngine =
                remember {
                    NavigationStartOrientationEngine()
                }

            val exitLookaheadEngine =
                remember {
                    NavigationExitLookaheadEngine()
                }

            val startOrientationVisibility =
                remember {
                    NavigationStartOrientationVisibilityController()
                }

            val routeBootstrap =
                remember {
                    NavigationRouteBootstrap
                        .load(
                            context =
                                applicationContext,

                            bridge =
                                bridge,
                        )
                }

            val liveRouteSource =
                remember {
                    NavigationRouteSourceFactory
                        .fromManifest(
                            applicationContext
                        )
                }

            val routeLifecycleController =
                remember(
                    liveRouteSource
                ) {
                    liveRouteSource
                        ?.let {
                                source ->

                            NavigationRouteLifecycleController(
                                bridge =
                                    bridge,

                                source =
                                    source,
                            )
                        }
                }

            val initialRouteRequest =
                remember {
                    NavigationRouteIntentRequest
                        .fromIntent(
                            intent
                        )
                }

            var tripPlan by
                remember(
                    initialRouteRequest
                ) {
                    mutableStateOf(
                        NavigationTripPlan
                            .fromRequest(
                                initialRouteRequest
                            )
                    )
                }

            var selectedTripStop by
                remember {
                    mutableStateOf<
                        NavigationTripStop?
                    >(
                        null
                    )
                }

            var snapshot by
                remember {
                    mutableStateOf(
                        routeBootstrap
                            .snapshot
                    )
                }

            var routeAcquisitionTelemetry by
                remember {
                    mutableStateOf(
                        NavigationRouteAcquisitionTelemetry(
                            state =
                                NavigationRouteAcquisitionState.FallbackReady,

                            message =
                                if (
                                    routeLifecycleController ==
                                        null
                                ) {
                                    "Live-Endpunkt nicht konfiguriert – Fallback-Route aktiv"
                                } else {
                                    "Fallback-Route bereit"
                                },
                        )
                    )
                }

            DisposableEffect(
                routeLifecycleController,
                initialRouteRequest,
            ) {
                val controller =
                    routeLifecycleController

                if (
                    controller !=
                        null
                ) {
                    controller.loadInitial(
                        request =
                            initialRouteRequest,

                        snapshotProvider = {
                            snapshot
                        },

                        onSnapshot = {
                                updatedSnapshot ->

                            snapshot =
                                updatedSnapshot
                        },

                        onTelemetry = {
                                updatedAcquisition ->

                            routeAcquisitionTelemetry =
                                updatedAcquisition
                        },
                    )
                }

                onDispose {
                    controller
                        ?.close()
                }
            }

            var telemetry by
                remember {
                    mutableStateOf(
                        NavigationRuntimeTelemetry
                            .stopped()
                    )
                }

            var progressStep by
                remember {
                    mutableStateOf(
                        0
                    )
                }

            var navigationStartedAtNanos by
                remember {
                    mutableStateOf<Long?>(
                        null
                    )
                }

            var locationPermissionGranted by
                remember {
                    mutableStateOf(
                        hasNavigationLocationPermission(
                            applicationContext
                        )
                    )
                }

            var preciseLocationGranted by
                remember {
                    mutableStateOf(
                        hasPreciseNavigationLocationPermission(
                            applicationContext
                        )
                    )
                }

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract =
                        ActivityResultContracts
                            .RequestMultiplePermissions()
                ) {
                    locationPermissionGranted =
                        hasNavigationLocationPermission(
                            applicationContext
                        )

                    preciseLocationGranted =
                        hasPreciseNavigationLocationPermission(
                            applicationContext
                        )
                }

            val saveFavoriteDestinations:
                (
                    FavoriteDestinationCollection,
                    String,
                ) -> Unit =
                {
                    updated,
                    successMessage ->

                    val saved =
                        runCatching {
                            favoriteDestinationStore
                                .save(
                                    updated
                                )
                        }.getOrDefault(
                            false
                        )

                    if (
                        saved
                    ) {
                        favoriteDestinations =
                            updated

                        destinationPlannerMessage =
                            successMessage
                    } else {
                        destinationPlannerMessage =
                            "Favorit konnte nicht gespeichert werden."
                    }
                }

            val searchDestination:
                (String) -> Unit =
                search@ {
                    rawQuery ->

                    if (
                        snapshot.state !=
                            NavigationSessionState.Preview
                    ) {
                        destinationPlannerMessage =
                            "Navigation zuerst stoppen, bevor ein neues Ziel gesucht wird."

                        return@search
                    }

                    if (
                        destinationPlannerBusy
                    ) {
                        return@search
                    }

                    val query =
                        rawQuery.trim()

                    if (
                        query.length !in
                            2..160 ||
                        query.any {
                            it.code <
                                0x20 ||
                                it.code ==
                                0x7f
                        }
                    ) {
                        destinationPlannerMessage =
                            "Bitte mindestens zwei gültige Zeichen eingeben."

                        return@search
                    }

                    destinationSearchHandle
                        ?.cancel()

                    destinationSearchHandle =
                        null

                    destinationPlannerBusy =
                        true

                    destinationPlannerMessage =
                        "Zielsuche läuft …"

                    destinationSearchHandle =
                        runCatching {
                            destinationSearchSource
                                .search(
                                    query
                                ) {
                                        result ->

                                    destinationSearchHandle =
                                        null

                                    destinationPlannerBusy =
                                        false

                                    result.fold(
                                        onSuccess = {
                                                results ->

                                            destinationSearchResults =
                                                results

                                            destinationPlannerMessage =
                                                if (
                                                    results.isEmpty()
                                                ) {
                                                    "Keine passenden Ziele gefunden."
                                                } else {
                                                    "${results.size} Zieltreffer gefunden."
                                                }
                                        },

                                        onFailure = {
                                                error ->

                                            destinationSearchResults =
                                                emptyList()

                                            destinationPlannerMessage =
                                                error.message
                                                    ?: "Zielsuche fehlgeschlagen."
                                        },
                                    )
                                }
                        }.getOrElse {
                                error ->

                            destinationPlannerBusy =
                                false

                            destinationPlannerMessage =
                                error.message
                                    ?: "Zielsuche konnte nicht gestartet werden."

                            null
                        }
                }

            val requestTripPlanRoute:
                (NavigationTripPlan) -> Unit =
                routeRequest@ {
                    requestedPlan ->

                    if (
                        snapshot.state !=
                            NavigationSessionState.Preview
                    ) {
                        destinationPlannerMessage =
                            "Navigation zuerst stoppen, bevor der Reiseplan geändert wird."

                        return@routeRequest
                    }

                    if (
                        destinationPlannerBusy
                    ) {
                        return@routeRequest
                    }

                    val controller =
                        routeLifecycleController

                    if (
                        controller ==
                            null
                    ) {
                        destinationPlannerMessage =
                            "Live-Routing ist nicht konfiguriert."

                        return@routeRequest
                    }

                    if (
                        !hasPreciseNavigationLocationPermission(
                            applicationContext
                        )
                    ) {
                        destinationPlannerMessage =
                            "Präzise Standortfreigabe erforderlich. Nach der Freigabe Ziel erneut bestätigen."

                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            )
                        )

                        return@routeRequest
                    }

                    destinationSearchHandle
                        ?.cancel()

                    destinationSearchHandle =
                        null

                    destinationPlannerBusy =
                        true

                    destinationPlannerMessage =
                        "Aktuelle Position wird für die Routenplanung bestimmt …"

                    planningLocationController
                        .request {
                                planningResult ->

                            planningResult.fold(
                                onSuccess = {
                                        planningLocation ->

                                    val request =
                                        requestedPlan
                                            .toRouteRequest(
                                                origin =
                                                    planningLocation
                                                        .position,

                                                family =
                                                    initialRouteRequest
                                                        .family,
                                            )

                                    destinationPlannerMessage =
                                        "Routenvorschau wird berechnet …"

                                    controller
                                        .loadInitial(
                                            request =
                                                request,

                                            snapshotProvider = {
                                                snapshot
                                            },

                                            onSnapshot = {
                                                    updatedSnapshot ->

                                                /*
                                                 * Installing a newly
                                                 * planned route is still
                                                 * a Preview-only route-data
                                                 * operation. It does not
                                                 * start navigation and does
                                                 * not create JNI progress.
                                                 */
                                                snapshot =
                                                    updatedSnapshot

                                                tripPlan =
                                                    requestedPlan

                                                selectedTripStop =
                                                    null

                                                progressStep =
                                                    0
                                            },

                                            onTelemetry = {
                                                    updatedAcquisition ->

                                                routeAcquisitionTelemetry =
                                                    updatedAcquisition

                                                when (
                                                    updatedAcquisition
                                                        .state
                                                ) {
                                                    NavigationRouteAcquisitionState.LoadingInitial -> {
                                                        destinationPlannerBusy =
                                                            true

                                                        destinationPlannerMessage =
                                                            "Routenvorschau wird berechnet …"
                                                    }

                                                    NavigationRouteAcquisitionState.LiveReady -> {
                                                        destinationPlannerBusy =
                                                            false

                                                        destinationPlannerMessage =
                                                            "Routenvorschau aktualisiert."
                                                    }

                                                    NavigationRouteAcquisitionState.LiveFailed,
                                                    NavigationRouteAcquisitionState.RerouteFailed -> {
                                                        destinationPlannerBusy =
                                                            false

                                                        destinationPlannerMessage =
                                                            updatedAcquisition
                                                                .message
                                                    }

                                                    NavigationRouteAcquisitionState.FallbackReady -> {
                                                        destinationPlannerBusy =
                                                            false

                                                        destinationPlannerMessage =
                                                            updatedAcquisition
                                                                .message
                                                    }

                                                    NavigationRouteAcquisitionState.Rerouting -> {
                                                        destinationPlannerBusy =
                                                            true

                                                        destinationPlannerMessage =
                                                            updatedAcquisition
                                                                .message
                                                    }
                                                }
                                            },
                                        )
                                },

                                onFailure = {
                                        error ->

                                    destinationPlannerBusy =
                                        false

                                    destinationPlannerMessage =
                                        error.message
                                            ?: "Aktuelle Position ist für die Routenplanung nicht verfügbar."
                                },
                            )
                        }
                }

            /*
             * Diagnostic native fallback.
             *
             * It remains available only while the automatic
             * precise-position pipeline is NOT active.
             */
            val progressUpdates =
                remember(
                    snapshot.sessionId,
                    snapshot.routeId,
                    snapshot.geometry.size,
                ) {
                    buildDiagnosticProgressAnchors(
                        routePointCount =
                            snapshot.geometry.size
                    )
                }

            DisposableEffect(
                snapshot.state
            ) {
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating
                ) {
                    window.addFlags(
                        WindowManager.LayoutParams
                            .FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    window.clearFlags(
                        WindowManager.LayoutParams
                            .FLAG_KEEP_SCREEN_ON
                    )
                }

                onDispose {
                    window.clearFlags(
                        WindowManager.LayoutParams
                            .FLAG_KEEP_SCREEN_ON
                    )
                }
            }

            /*
             * Automatic navigation runtime lifecycle.
             *
             * The effect is keyed on SESSION STATE and location
             * permission only. Native progress snapshot updates
             * therefore do not restart the sensor stack.
             */
            DisposableEffect(
                snapshot.state,
                snapshot.sessionId,
                locationPermissionGranted,
                preciseLocationGranted,
                runtimeController,
            ) {
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating &&
                    locationPermissionGranted &&
                    preciseLocationGranted
                ) {
                    runtimeController.start(
                        route =
                            snapshot.geometry,

                        initialProgress =
                            RouteProgressAnchor(
                                shapeSegmentIndex =
                                    snapshot
                                        .shapeSegmentIndex,

                                segmentFraction =
                                    snapshot
                                        .segmentFraction,
                            ),

                        onSnapshot = {
                                updatedSnapshot ->

                            snapshot =
                                updatedSnapshot
                        },

                        onTelemetry = {
                                updatedTelemetry ->

                            driveProofObservationSink
                                .record(
                                    snapshot =
                                        snapshot,

                                    telemetry =
                                        updatedTelemetry,

                                    capturedAtElapsedRealtimeNanos =
                                        SystemClock
                                            .elapsedRealtimeNanos(),
                                )

                            telemetry =
                                updatedTelemetry

                            routeLifecycleController
                                ?.observeTelemetry(
                                    telemetry =
                                        updatedTelemetry,

                                    snapshotProvider = {
                                        snapshot
                                    },

                                    onSnapshot = {
                                            replacementSnapshot ->

                                        /*
                                         * Native session changed.
                                         *
                                         * Stop the old sensor/matcher
                                         * lifecycle before Compose starts
                                         * it again for the new session id.
                                         */
                                        runtimeController
                                            .reset()

                                        progressStep =
                                            0

                                        navigationStartedAtNanos =
                                            SystemClock
                                                .elapsedRealtimeNanos()

                                        telemetry =
                                            NavigationRuntimeTelemetry
                                                .stopped()

                                        snapshot =
                                            replacementSnapshot
                                    },

                                    onTelemetry = {
                                            updatedAcquisition ->

                                        routeAcquisitionTelemetry =
                                            updatedAcquisition
                                    },
                                )
                        },
                    )
                } else {
                    runtimeController.stop()
                }

                onDispose {
                    runtimeController.stop()
                }
            }

            val startOrientation =
                remember(
                    snapshot.sessionId,
                    snapshot.routeId,
                    snapshot.geometry,
                    snapshot.routeManeuvers,
                ) {
                    startOrientationEngine.calculate(
                        route =
                            snapshot.geometry,

                        roadContext =
                            buildNavigationStartRoadContext(
                                snapshot
                                    .routeManeuvers
                            ),
                    )
                }

            val orientationProgress =
                telemetry
                    .acceptedProgress
                    ?: RouteProgressAnchor(
                        shapeSegmentIndex =
                            snapshot
                                .shapeSegmentIndex,

                        segmentFraction =
                            snapshot
                                .segmentFraction,
                    )

            val routeEvents =
                remember(
                    snapshot.sessionId,
                    snapshot.routeId,
                    snapshot.geometry.size,
                    snapshot.routeManeuvers,
                ) {
                    buildNavigationRouteEvents(
                        routePointCount =
                            snapshot.geometry.size,

                        maneuvers =
                            snapshot.routeManeuvers,
                    )
                }

            val criticalEventAhead =
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating &&
                    telemetry.safetyStatus ==
                        NavigationRouteProgressSafetyStatus.Accepted
                ) {
                    exitLookaheadEngine
                        .criticalEventAhead(
                            route =
                                snapshot.geometry,

                            currentProgress =
                                orientationProgress,

                            events =
                                routeEvents,
                        )
                } else {
                    null
                }

            val distanceFromStartM =
                runCatching {
                    alongRouteDistanceMeters(
                        route =
                            snapshot.geometry,

                        anchor =
                            orientationProgress,
                    )
                }.getOrDefault(
                    0.0
                )

            val navigationElapsedNanos =
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating
                ) {
                    val startedAt =
                        navigationStartedAtNanos

                    if (
                        startedAt ==
                            null
                    ) {
                        0L
                    } else {
                        (
                            SystemClock
                                .elapsedRealtimeNanos() -
                                startedAt
                        ).coerceAtLeast(
                            0L
                        )
                    }
                } else {
                    0L
                }

            val showStartOrientation =
                startOrientationVisibility
                    .shouldShow(
                        state =
                            snapshot.state,

                        distanceFromStartM =
                            distanceFromStartM,

                        navigationElapsedNanos =
                            navigationElapsedNanos,
                    )

            val automaticPreciseProgressActive =
                telemetry
                    .automaticProgressActive &&
                    preciseLocationGranted

            /*
             * Observation is presentation-only here.
             *
             * It never replaces accepted route progress. The
             * presentation object itself decides conservatively
             * whether camera following is permitted.
             */
            val observedPositionPresentation =
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating
                ) {
                    NavigationObservedPositionPresentation
                        .create(
                            position =
                                telemetry
                                    .lastObservedPosition,

                            accuracyM =
                                telemetry
                                    .lastLocationAccuracyM,

                            confidence =
                                telemetry
                                    .confidence,

                            safetyStatus =
                                telemetry
                                    .safetyStatus,

                            fusionMode =
                                telemetry
                                    .fusionMode,
                        )
                } else {
                    null
                }

            RoutingPlatformTheme {
                Box(
                    modifier =
                        Modifier.fillMaxSize(),
                ) {
                    NavigationScreen(
                        snapshot =
                            snapshot,

                        navigationStartEnabled =
                            routeBootstrap
                                .productionRouteReady &&
                                routeAcquisitionTelemetry
                                    .state !=
                                    NavigationRouteAcquisitionState
                                        .LoadingInitial &&
                                !destinationPlannerBusy,

                        navigationUnavailableMessage =
                            if (
                                routeAcquisitionTelemetry
                                    .state ==
                                    NavigationRouteAcquisitionState
                                        .LoadingInitial
                            ) {
                                routeAcquisitionTelemetry
                                    .message
                            } else {
                                routeBootstrap
                                    .errorMessage
                            },

                        routeAcquisitionMessage =
                            routeAcquisitionTelemetry
                                .message,

                        routeAcquisitionState =
                            routeAcquisitionTelemetry
                                .state,

                        routeAcquisitionFaultCode =
                            routeAcquisitionTelemetry
                                .fault
                                ?.code,

                        manualProgressEnabled =
                            !automaticPreciseProgressActive,

                        observedPosition =
                            observedPositionPresentation,

                        trustedTravelBearingDegrees =
                            telemetry
                                .trustedTravelBearingDegrees,

                        displayPreferences =
                            activeProfile
                                .display,

                        personalityPreferences =
                            activeProfile
                                .personality,

                        selectedTripStop =
                            selectedTripStop,

                        tripPlan =
                            tripPlan,

                        favoriteDestinations =
                            favoriteDestinations,

                        destinationSearchResults =
                            destinationSearchResults,

                        destinationPlannerMessage =
                            destinationPlannerMessage,

                        destinationPlannerBusy =
                            destinationPlannerBusy,

                        onMapTargetSelected = {
                                point ->

                            if (
                                snapshot.state ==
                                    NavigationSessionState.Preview
                            ) {
                                selectedTripStop =
                                    NavigationTripStop(
                                        point =
                                            point,

                                        label =
                                            "Kartenpunkt",
                                    )

                                destinationPlannerMessage =
                                    "Kartenpunkt ausgewählt."
                            }
                        },

                        onSearchDestination =
                            searchDestination,

                        onSearchResultSelected = {
                                result ->

                            if (
                                snapshot.state ==
                                    NavigationSessionState.Preview
                            ) {
                                selectedTripStop =
                                    NavigationTripStop(
                                        point =
                                            result.point,

                                        label =
                                            result.displayText,
                                    )

                                destinationPlannerMessage =
                                    "Suchtreffer ausgewählt."
                            }
                        },

                        onFavoriteSelected = {
                                favorite ->

                            if (
                                snapshot.state ==
                                    NavigationSessionState.Preview
                            ) {
                                selectedTripStop =
                                    NavigationTripStop(
                                        point =
                                            favorite.point,

                                        label =
                                            favorite.label,
                                    )

                                destinationPlannerMessage =
                                    "${favorite.label} ausgewählt."
                            }
                        },

                        onSaveSelectedAsHome = {
                            selectedTripStop
                                ?.let {
                                        selected ->

                                    saveFavoriteDestinations(
                                        favoriteDestinations
                                            .withHome(
                                                selected.point
                                            ),

                                        "Zuhause gespeichert.",
                                    )
                                }
                        },

                        onSaveSelectedAsWork = {
                            selectedTripStop
                                ?.let {
                                        selected ->

                                    saveFavoriteDestinations(
                                        favoriteDestinations
                                            .withWork(
                                                selected.point
                                            ),

                                        "Arbeit gespeichert.",
                                    )
                                }
                        },

                        onSaveSelectedAsCustom = {
                                label ->

                            val selected =
                                selectedTripStop

                            if (
                                selected ==
                                    null
                            ) {
                                destinationPlannerMessage =
                                    "Zuerst ein Ziel auswählen."
                            } else {
                                val trimmedLabel =
                                    label.trim()

                                if (
                                    trimmedLabel.isEmpty()
                                ) {
                                    destinationPlannerMessage =
                                        "Favoritenname darf nicht leer sein."
                                } else {
                                    runCatching {
                                        favoriteDestinations
                                            .addCustom(
                                                id =
                                                    "custom-" +
                                                        java.util.UUID
                                                            .randomUUID()
                                                            .toString(),

                                                label =
                                                    trimmedLabel,

                                                point =
                                                    selected.point,
                                            )
                                    }.onSuccess {
                                            updated ->

                                        saveFavoriteDestinations(
                                            updated,
                                            "Favorit gespeichert.",
                                        )
                                    }.onFailure {
                                            error ->

                                        destinationPlannerMessage =
                                            error.message
                                                ?: "Favorit konnte nicht gespeichert werden."
                                    }
                                }
                            }
                        },

                        onDeleteFavorite = {
                                favoriteId ->

                            runCatching {
                                favoriteDestinations
                                    .remove(
                                        favoriteId
                                    )
                            }.onSuccess {
                                    updated ->

                                saveFavoriteDestinations(
                                    updated,
                                    "Favorit gelöscht.",
                                )
                            }.onFailure {
                                    error ->

                                destinationPlannerMessage =
                                    error.message
                                        ?: "Favorit konnte nicht gelöscht werden."
                            }
                        },

                        onUseSelectedAsDestination = {
                            selectedTripStop
                                ?.let {
                                        selected ->

                                    requestTripPlanRoute(
                                        tripPlan
                                            .withDestination(
                                                selected
                                            )
                                    )
                                }
                        },

                        onAppendSelectedVia = {
                            selectedTripStop
                                ?.let {
                                        selected ->

                                    runCatching {
                                        tripPlan
                                            .appendVia(
                                                selected
                                            )
                                    }.onSuccess {
                                            updatedPlan ->

                                        requestTripPlanRoute(
                                            updatedPlan
                                        )
                                    }.onFailure {
                                            error ->

                                        destinationPlannerMessage =
                                            error.message
                                                ?: "Zwischenziel konnte nicht hinzugefügt werden."
                                    }
                                }
                        },

                        onClearSelectedTarget = {
                            selectedTripStop =
                                null

                            destinationPlannerMessage =
                                "Markierung entfernt."
                        },

                        onRemoveVia = {
                                index ->

                            runCatching {
                                tripPlan
                                    .removeVia(
                                        index
                                    )
                            }.onSuccess {
                                    updatedPlan ->

                                requestTripPlanRoute(
                                    updatedPlan
                                )
                            }.onFailure {
                                    error ->

                                destinationPlannerMessage =
                                    error.message
                                        ?: "Zwischenziel konnte nicht gelöscht werden."
                            }
                        },

                        onMoveViaUp = {
                                index ->

                            runCatching {
                                tripPlan
                                    .moveViaUp(
                                        index
                                    )
                            }.onSuccess {
                                    updatedPlan ->

                                requestTripPlanRoute(
                                    updatedPlan
                                )
                            }.onFailure {
                                    error ->

                                destinationPlannerMessage =
                                    error.message
                                        ?: "Zwischenziel konnte nicht verschoben werden."
                            }
                        },

                        onMoveViaDown = {
                                index ->

                            runCatching {
                                tripPlan
                                    .moveViaDown(
                                        index
                                    )
                            }.onSuccess {
                                    updatedPlan ->

                                requestTripPlanRoute(
                                    updatedPlan
                                )
                            }.onFailure {
                                    error ->

                                destinationPlannerMessage =
                                    error.message
                                        ?: "Zwischenziel konnte nicht verschoben werden."
                            }
                        },

                        onNavigationControlSideChanged = {
                                side ->

                            val updated =
                                activeProfile.copy(
                                    display =
                                        activeProfile
                                            .display
                                            .copy(
                                                navigationControlSide =
                                                    side
                                            )
                                )

                            check(
                                profileStore
                                    .saveAndActivate(
                                        updated
                                    )
                            ) {
                                "Could not persist active profile."
                            }

                            activeProfile =
                                updated
                        },

                        onExperiencePackSelected = {
                                packId ->

                            val updated =
                                activeProfile.copy(
                                    personality =
                                        activeProfile
                                            .personality
                                            .copy(
                                                selectedPackId =
                                                    packId,

                                                selectionSource =
                                                    ExperiencePackSelectionSource
                                                        .Explicit,

                                                weeklyDiscoveryEnabled =
                                                    false,
                                            )
                                )

                            check(
                                profileStore
                                    .saveAndActivate(
                                        updated
                                    )
                            ) {
                                "Could not persist active profile."
                            }

                            activeProfile =
                                updated
                        },

                        onWeeklyDiscoveryChanged = {
                                enabled ->

                            val updated =
                                activeProfile.copy(
                                    personality =
                                        activeProfile
                                            .personality
                                            .copy(
                                                weeklyDiscoveryEnabled =
                                                    enabled,

                                                selectionSource =
                                                    if (
                                                        enabled
                                                    ) {
                                                        ExperiencePackSelectionSource
                                                            .WeeklyDiscovery
                                                    } else {
                                                        ExperiencePackSelectionSource
                                                            .Explicit
                                                    },
                                            )
                                )

                            check(
                                profileStore
                                    .saveAndActivate(
                                        updated
                                    )
                            ) {
                                "Could not persist active profile."
                            }

                            activeProfile =
                                updated
                        },

                        onWeeklyDiscoveryIntensityChanged = {
                                intensity ->

                            val updated =
                                activeProfile.copy(
                                    personality =
                                        activeProfile
                                            .personality
                                            .copy(
                                                weeklyDiscoveryIntensity =
                                                    intensity
                                            )
                                )

                            check(
                                profileStore
                                    .saveAndActivate(
                                        updated
                                    )
                            ) {
                                "Could not persist active profile."
                            }

                            activeProfile =
                                updated
                        },

                        onStartNavigation = {
                            /*
                             * progressStep deliberately survives a
                             * stop/start cycle because native route
                             * progress is also preserved.
                             */
                            runtimeController.reset()

                            telemetry =
                                NavigationRuntimeTelemetry
                                    .stopped()

                            navigationStartedAtNanos =
                                SystemClock
                                    .elapsedRealtimeNanos()

                            locationPermissionGranted =
                                hasNavigationLocationPermission(
                                    applicationContext
                                )

                            preciseLocationGranted =
                                hasPreciseNavigationLocationPermission(
                                    applicationContext
                                )

                            snapshot =
                                bridge
                                    .startNavigation()

                            val permissions =
                                navigationRuntimePermissionsToRequest(
                                    applicationContext
                                )

                            if (
                                permissions.isNotEmpty()
                            ) {
                                permissionLauncher.launch(
                                    permissions
                                )
                            }
                        },

                        onStopNavigation = {
                            /*
                             * First stop all automatic observation/
                             * matcher activity so no late callback can
                             * race the native Navigating -> Preview
                             * transition.
                             */
                            runtimeController.reset()

                            telemetry =
                                NavigationRuntimeTelemetry
                                    .stopped(
                                        acceptedProgress =
                                            RouteProgressAnchor(
                                                shapeSegmentIndex =
                                                    snapshot
                                                        .shapeSegmentIndex,

                                                segmentFraction =
                                                    snapshot
                                                        .segmentFraction,
                                            )
                                    )

                            navigationStartedAtNanos =
                                null

                            snapshot =
                                bridge
                                    .stopNavigation()
                        },

                        onAdvanceProgress = {
                            if (
                                !automaticPreciseProgressActive &&
                                progressStep <
                                    progressUpdates.size
                            ) {
                                val update =
                                    progressUpdates[
                                        progressStep
                                    ]

                                snapshot =
                                    bridge
                                        .updateProgress(
                                            shapeSegmentIndex =
                                                update
                                                    .shapeSegmentIndex,

                                            segmentFraction =
                                                update
                                                    .segmentFraction,
                                        )

                                progressStep +=
                                    1
                            }
                        },
                    )

                    NavigationAssistOverlay(
                        criticalEventAhead =
                            criticalEventAhead,

                        startOrientation =
                            if (
                                showStartOrientation
                            ) {
                                startOrientation
                            } else {
                                null
                            },

                        positionConfidence =
                            telemetry
                                .confidence,

                        runtimeStatus =
                            telemetry
                                .pipelineStatus,

                        showRuntimeStatus =
                            snapshot.state ==
                                NavigationSessionState.Navigating,

                        modifier =
                            Modifier.align(
                                Alignment.TopEnd
                            ),
                    )
                }
            }
        }
    }
}