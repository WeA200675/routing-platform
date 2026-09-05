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
import org.routingplatform.app.navigation.AndroidNavigationRuntimeController
import org.routingplatform.app.navigation.JniNavigationCoreBridge
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
import org.routingplatform.app.navigation.RouteProgressAnchor
import org.routingplatform.app.navigation.alongRouteDistanceMeters
import org.routingplatform.app.navigation.buildDiagnosticProgressAnchors
import org.routingplatform.app.navigation.buildNavigationRouteEvents
import org.routingplatform.app.navigation.buildNavigationStartRoadContext
import org.routingplatform.app.navigation.hasNavigationLocationPermission
import org.routingplatform.app.navigation.hasPreciseNavigationLocationPermission
import org.routingplatform.app.navigation.navigationRuntimePermissionsToRequest
import org.routingplatform.app.ui.NavigationAssistOverlay
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

            val runtimeController =
                remember {
                    AndroidNavigationRuntimeController(
                        context =
                            applicationContext,

                        bridge =
                            bridge,
                    )
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
                                        .LoadingInitial,

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

                        manualProgressEnabled =
                            !automaticPreciseProgressActive,

                        onStartNavigation = {
                            progressStep =
                                0

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