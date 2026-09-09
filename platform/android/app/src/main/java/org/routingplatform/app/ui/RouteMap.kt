package org.routingplatform.app.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.routingplatform.app.navigation.NavigationRecoveryDecision
import org.routingplatform.app.navigation.NavigationRecoveryPolicy
import org.routingplatform.app.navigation.NavigationReliabilityClassifier
import org.routingplatform.app.navigation.NavigationReliabilityEvent
import org.routingplatform.app.navigation.NavigationReliabilityEventKind
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.navigation.splitRouteProgressGeometry
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.ProfileMapStyle

private fun logNavigationMapReliabilityEvent(
    event:
        NavigationReliabilityEvent,
) {
    Log.w(
        NAVIGATION_MAP_LOG_TAG,
        "${event.kind}/${event.fault.code}/${event.fault.domain}",
    )
}

private const val NAVIGATION_MAP_LOG_TAG =
    "RoutingPlatformMap"

/*
 * Presentation boundary only.
 *
 * This component draws:
 *  - an external street-map style,
 *  - the immutable installed route,
 *  - accepted route-progress geometry.
 *
 * The progress marker is deliberately NOT presented as an observed
 * GNSS position. A separate observed-position layer is added only
 * when the positioning/safety contract is wired in G3.
 */
@Composable
fun RouteMap(
    points: List<RoutePoint>,
    shapeSegmentIndex: Int,
    segmentFraction: Double,
    showProgress: Boolean,
    modifier: Modifier = Modifier,

    displayPreferences:
        DisplayPreferences =
        DisplayPreferences(),

    automaticNightMode:
        Boolean =
        false,

    observedPosition:
        NavigationObservedPositionPresentation? =
        null,

    trustedTravelBearingDegrees:
        Double? =
        null,

    selectedTarget:
        RoutePoint? =
        null,

    mapSelectionEnabled:
        Boolean =
        false,

    onMapLongPress:
        (RoutePoint) -> Unit =
        {},

    onReliabilityEvent:
        (NavigationReliabilityEvent) -> Unit =
        ::logNavigationMapReliabilityEvent,
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val currentReliabilityEvent by
        rememberUpdatedState(
            onReliabilityEvent
        )

    val mapRecoveryPolicy =
        remember {
            NavigationRecoveryPolicy(
                maxInfrastructureRetries =
                    1,

                retryCooldownMs =
                    400L,
            )
        }

    val effectiveMapStyle =
        if (automaticNightMode) {
            ProfileMapStyle.Night
        } else {
            displayPreferences
                .mapStyle
        }

    val styleDescriptor =
        NavigationMapPresentation
            .styleDescriptor(
                effectiveMapStyle
            )

    /*
     * Heading-up is a presentation decision only.
     *
     * The existing G3 camera gate must be open AND the runtime must
     * supply a current trusted travel bearing. Otherwise the map
     * remains north-up.
     */
    val compassPresentation =
        remember(
            displayPreferences
                .mapOrientation,

            trustedTravelBearingDegrees,

            observedPosition
                ?.cameraFollowAllowed,
        ) {
            NavigationCompassPresentation
                .create(
                    mapOrientation =
                        displayPreferences
                            .mapOrientation,

                    trustedTravelBearingDegrees =
                        trustedTravelBearingDegrees,

                    cameraFollowAllowed =
                        observedPosition
                            ?.cameraFollowAllowed ==
                            true,
                )
        }

    val mapView =
        remember {
            MapView(context).also {
                it.onCreate(null)
            }
        }

    var mapLibreMap by
        remember {
            mutableStateOf<MapLibreMap?>(
                null
            )
        }

    var loadedStyle by
        remember {
            mutableStateOf<Style?>(
                null
            )
        }

    var mapLoadState by
        remember {
            mutableStateOf(
                NavigationMapLoadState.Loading
            )
        }

    var mapLoadRetryCount by
        remember(
            styleDescriptor.styleUri
        ) {
            mutableStateOf(
                0
            )
        }

    var mapReloadGeneration by
        remember(
            styleDescriptor.styleUri
        ) {
            mutableStateOf(
                0
            )
        }

    var pendingMapRetry by
        remember(
            styleDescriptor.styleUri
        ) {
            mutableStateOf<
                NavigationRecoveryDecision.Retry?
            >(
                null
            )
        }

    val handleMapFailure:
        (String) -> Unit = {
                detail ->

            if (
                loadedStyle ==
                    null &&
                pendingMapRetry ==
                    null
            ) {
                val fault =
                    NavigationReliabilityClassifier
                        .mapUnavailable(
                            detail
                        )

                currentReliabilityEvent(
                    NavigationReliabilityEvent(
                        kind =
                            NavigationReliabilityEventKind.FaultObserved,

                        fault =
                            fault,

                        retryNumber =
                            mapLoadRetryCount,

                        delayMs =
                            0L,
                    )
                )

                when (
                    val decision =
                        mapRecoveryPolicy
                            .decide(
                                fault =
                                    fault,

                                retriesAlreadyAttempted =
                                    mapLoadRetryCount,
                            )
                ) {
                    is NavigationRecoveryDecision.Retry -> {
                        mapLoadRetryCount =
                            decision.retryNumber

                        pendingMapRetry =
                            decision

                        mapLoadState =
                            NavigationMapLoadState
                                .Loading

                        currentReliabilityEvent(
                            NavigationReliabilityEvent(
                                kind =
                                    NavigationReliabilityEventKind.RecoveryScheduled,

                                fault =
                                    fault,

                                retryNumber =
                                    decision.retryNumber,

                                delayMs =
                                    decision.delayMs,
                            )
                        )
                    }

                    NavigationRecoveryDecision.FailClosed -> {
                        /*
                         * Map presentation degrades after the bounded
                         * retry budget. Navigation truth/session state
                         * is not changed here.
                         */
                        mapLoadState =
                            NavigationMapLoadState
                                .Failed

                        currentReliabilityEvent(
                            NavigationReliabilityEvent(
                                kind =
                                    NavigationReliabilityEventKind.RecoveryExhausted,

                                fault =
                                    fault,

                                retryNumber =
                                    mapLoadRetryCount,

                                delayMs =
                                    0L,
                            )
                        )
                    }
                }
            }
        }

    DisposableEffect(
        mapLibreMap,
        mapSelectionEnabled,
        onMapLongPress,
    ) {
        val map =
            mapLibreMap

        val longClickListener:
            MapLibreMap.OnMapLongClickListener? =
            if (
                map !=
                    null &&
                mapSelectionEnabled
            ) {
                MapLibreMap
                    .OnMapLongClickListener {
                            point ->

                        onMapLongPress(
                            RoutePoint(
                                latitude =
                                    point.latitude,

                                longitude =
                                    point.longitude,
                            )
                        )

                        true
                    }
            } else {
                null
            }

        longClickListener
            ?.let {
                    listener ->

                map?.addOnMapLongClickListener(
                    listener
                )
            }

        onDispose {
            longClickListener
                ?.let {
                        listener ->

                    map?.removeOnMapLongClickListener(
                        listener
                    )
                }
        }
    }

    DisposableEffect(
        lifecycleOwner,
        mapView,
    ) {
        val lifecycleObserver =
            LifecycleEventObserver {
                    _,
                    event ->

                when (event) {
                    Lifecycle.Event.ON_START ->
                        mapView.onStart()

                    Lifecycle.Event.ON_RESUME ->
                        mapView.onResume()

                    Lifecycle.Event.ON_PAUSE ->
                        mapView.onPause()

                    Lifecycle.Event.ON_STOP ->
                        mapView.onStop()

                    else ->
                        Unit
                }
            }

        val failureListener =
            MapView.OnDidFailLoadingMapListener {
                    failureReason ->

                handleMapFailure(
                    "MapLibre style load failed: " +
                        failureReason
                            .toString()
                            .take(
                                300
                            )
                )
            }

        val renderListener =
            MapView.OnDidFinishRenderingMapListener {
                    fully ->

                if (
                    fully &&
                    loadedStyle !=
                        null
                ) {
                    mapLoadState =
                        NavigationMapLoadState
                            .Ready
                }
            }

        lifecycleOwner
            .lifecycle
            .addObserver(
                lifecycleObserver
            )

        mapView
            .addOnDidFailLoadingMapListener(
                failureListener
            )

        mapView
            .addOnDidFinishRenderingMapListener(
                renderListener
            )

        onDispose {
            lifecycleOwner
                .lifecycle
                .removeObserver(
                    lifecycleObserver
                )

            mapView
                .removeOnDidFailLoadingMapListener(
                    failureListener
                )

            mapView
                .removeOnDidFinishRenderingMapListener(
                    renderListener
                )

            loadedStyle =
                null

            mapLibreMap =
                null

            mapView.onDestroy()
        }
    }

    LaunchedEffect(
        pendingMapRetry
    ) {
        val retry =
            pendingMapRetry
                ?: return@LaunchedEffect

        if (
            retry.delayMs >
                0L
        ) {
            delay(
                retry.delayMs
            )
        }

        if (
            pendingMapRetry ==
                retry
        ) {
            pendingMapRetry =
                null

            mapReloadGeneration +=
                1
        }
    }

    /*
     * Style changes are presentation changes only.
     *
     * A future profile switch can therefore replace the street
     * appearance without replacing the native navigation session.
     */
    LaunchedEffect(
        mapView,
        styleDescriptor.styleUri,
        mapReloadGeneration,
        points.size,
    ) {
        if (points.size < 2) {
            return@LaunchedEffect
        }

        loadedStyle =
            null

        mapLoadState =
            NavigationMapLoadState
                .Loading

        mapView.getMapAsync {
                map ->

            mapLibreMap =
                map

            map.setStyle(
                styleDescriptor
                    .styleUri
            ) {
                    style ->

                try {
                    val progressGeometry =
                        splitRouteProgressGeometry(
                            points =
                                points,

                            shapeSegmentIndex =
                                shapeSegmentIndex,

                            segmentFraction =
                                segmentFraction,
                        )

                    installNavigationLayers(
                        style =
                            style,

                        fullRoute =
                            points,

                        traveledRoute =
                            progressGeometry
                                .traveledPoints,

                        remainingRoute =
                            progressGeometry
                                .remainingPoints,

                        progressPosition =
                            progressGeometry
                                .currentPosition,

                        displayPreferences =
                            displayPreferences,

                        observedPosition =
                            observedPosition,

                        selectedTarget =
                            selectedTarget,
                    )

                    pendingMapRetry =
                        null

                    mapLoadRetryCount =
                        0

                    loadedStyle =
                        style

                    mapLoadState =
                        NavigationMapLoadState
                            .Rendering
                } catch (
                    error:
                        RuntimeException
                ) {
                    loadedStyle =
                        null

                    handleMapFailure(
                        "Map layer installation failed: " +
                            error.javaClass.name
                    )
                }
            }
        }
    }

    /*
     * Route/progress updates never reload the base street style.
     */
    LaunchedEffect(
        loadedStyle,
        mapLibreMap,
        points,
        shapeSegmentIndex,
        segmentFraction,
        showProgress,
        displayPreferences,
        observedPosition,
        selectedTarget,
        compassPresentation,
    ) {
        val style =
            loadedStyle
                ?: return@LaunchedEffect

        val map =
            mapLibreMap
                ?: return@LaunchedEffect

        if (points.size < 2) {
            return@LaunchedEffect
        }

        val progressGeometry =
            splitRouteProgressGeometry(
                points =
                    points,

                shapeSegmentIndex =
                    shapeSegmentIndex,

                segmentFraction =
                    segmentFraction,
            )

        style
            .getSourceAs<GeoJsonSource>(
                PREVIEW_ROUTE_SOURCE_ID
            )
            ?.setGeoJson(
                routeGeoJson(
                    points
                )
            )

        style
            .getSourceAs<GeoJsonSource>(
                REMAINING_ROUTE_SOURCE_ID
            )
            ?.setGeoJson(
                routeGeoJson(
                    progressGeometry
                        .remainingPoints
                )
            )

        style
            .getSourceAs<GeoJsonSource>(
                TRAVELED_ROUTE_SOURCE_ID
            )
            ?.setGeoJson(
                routeGeoJson(
                    progressGeometry
                        .traveledPoints
                )
            )

        style
            .getSourceAs<GeoJsonSource>(
                ROUTE_PROGRESS_SOURCE_ID
            )
            ?.setGeoJson(
                pointGeoJson(
                    progressGeometry
                        .currentPosition
                )
            )

        style
            .getSourceAs<GeoJsonSource>(
                OBSERVED_POSITION_SOURCE_ID
            )
            ?.setGeoJson(
                observedPosition
                    ?.let {
                        pointGeoJson(
                            it.position
                        )
                    }
                    ?: emptyFeatureCollectionGeoJson()
            )

        style
            .getSourceAs<GeoJsonSource>(
                OBSERVED_ACCURACY_SOURCE_ID
            )
            ?.setGeoJson(
                observedPosition
                    ?.let {
                        observedAccuracyGeoJson(
                            it
                        )
                    }
                    ?: emptyFeatureCollectionGeoJson()
            )

        style
            .getSourceAs<GeoJsonSource>(
                SELECTED_TARGET_SOURCE_ID
            )
            ?.setGeoJson(
                selectedTarget
                    ?.let {
                        pointGeoJson(
                            it
                        )
                    }
                    ?: emptyFeatureCollectionGeoJson()
            )

        val previewVisibility =
            if (showProgress) {
                Property.NONE
            } else {
                Property.VISIBLE
            }

        val progressVisibility =
            if (showProgress) {
                Property.VISIBLE
            } else {
                Property.NONE
            }

        style
            .getLayer(
                PREVIEW_ROUTE_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    previewVisibility
                ),

                PropertyFactory.lineWidth(
                    NavigationMapPresentation
                        .previewRouteLineWidth(
                            displayPreferences
                        )
                ),
            )

        style
            .getLayer(
                REMAINING_ROUTE_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                ),

                PropertyFactory.lineWidth(
                    NavigationMapPresentation
                        .activeRouteLineWidth(
                            displayPreferences
                        )
                ),
            )

        style
            .getLayer(
                TRAVELED_ROUTE_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                ),

                PropertyFactory.lineWidth(
                    NavigationMapPresentation
                        .activeRouteLineWidth(
                            displayPreferences
                        )
                ),
            )

        style
            .getLayer(
                ROUTE_PROGRESS_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                )
            )

        val observedVisibility =
            if (
                showProgress &&
                observedPosition !=
                    null
            ) {
                Property.VISIBLE
            } else {
                Property.NONE
            }

        val accuracyVisibility =
            if (
                showProgress &&
                observedPosition
                    ?.accuracyRadiusM !=
                    null
            ) {
                Property.VISIBLE
            } else {
                Property.NONE
            }

        style
            .getLayer(
                OBSERVED_POSITION_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    observedVisibility
                ),

                PropertyFactory.circleColor(
                    observedPosition
                        ?.markerColor
                        ?: "#6B7280"
                )
            )

        style
            .getLayer(
                OBSERVED_ACCURACY_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    accuracyVisibility
                ),

                PropertyFactory.fillColor(
                    observedPosition
                        ?.markerColor
                        ?: "#6B7280"
                )
            )

        if (showProgress) {
            /*
             * A raw/held observation may remain visible, but it is
             * never allowed to drag the camera.
             *
             * Only Accepted + High/Medium + DirectObservation
             * presentation state may become the camera target.
             *
             * Heading-up is allowed only through compassPresentation.
             * If heading evidence is missing/stale/unsafe, its map
             * bearing is exactly zero and the map remains north-up.
             */
            val cameraTarget =
                observedPosition
                    ?.takeIf {
                        it.cameraFollowAllowed
                    }
                    ?.position
                    ?: progressGeometry
                        .currentPosition

            map.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(
                        LatLng(
                            cameraTarget
                                .latitude,

                            cameraTarget
                                .longitude,
                        )
                    )
                    .zoom(
                        displayPreferences
                            .defaultZoom
                    )
                    .tilt(
                        displayPreferences
                            .mapTiltDegrees
                    )
                    .bearing(
                        compassPresentation
                            .mapBearingDegrees
                    )
                    .build()
        } else {
            if (
                selectedTarget !=
                    null
            ) {
                map.cameraPosition =
                    CameraPosition
                        .Builder()
                        .target(
                            LatLng(
                                selectedTarget
                                    .latitude,

                                selectedTarget
                                    .longitude,
                            )
                        )
                        .zoom(
                            displayPreferences
                                .defaultZoom
                                .coerceIn(
                                    14.0,
                                    18.0,
                                )
                        )
                        .bearing(
                            0.0
                        )
                        .tilt(
                            0.0
                        )
                        .build()
            } else {
                fitPreviewRoute(
                    map =
                        map,

                    points =
                        points,
                )
            }
        }
    }

    Box(
        modifier =
            modifier,
    ) {
        AndroidView(
            factory = {
                mapView
            },

            modifier =
                Modifier.fillMaxSize(),
        )

        NavigationCompass(
            presentation =
                compassPresentation,

            modifier =
                Modifier
                    .align(
                        Alignment.CenterEnd
                    )
                    .padding(
                        end =
                            8.dp,
                    ),
        )

        observedPosition
            ?.let {
                    observed ->

                Surface(
                    modifier =
                        Modifier
                            .align(
                                Alignment.BottomEnd
                            )
                            .padding(8.dp),

                    tonalElevation =
                        4.dp,

                    shape =
                        MaterialTheme
                            .shapes
                            .small,
                ) {
                    Text(
                        text =
                            observed
                                .statusText,

                        modifier =
                            Modifier.padding(
                                horizontal =
                                    10.dp,

                                vertical =
                                    6.dp,
                            ),

                        style =
                            MaterialTheme
                                .typography
                                .labelSmall,
                    )
                }
            }

        Surface(
            modifier =
                Modifier
                    .align(
                        Alignment.BottomStart
                    )
                    .padding(8.dp),

            tonalElevation =
                4.dp,

            shape =
                MaterialTheme
                    .shapes
                    .small,
        ) {
            Text(
                text =
                    NavigationMapPresentation
                        .mapStatusText(
                            mapLoadState
                        ),

                modifier =
                    Modifier.padding(
                        horizontal =
                            10.dp,

                        vertical =
                            6.dp,
                    ),

                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
            )
        }
    }
}

@Composable
private fun NavigationCompass(
    presentation:
        NavigationCompassPresentation,

    modifier:
        Modifier =
        Modifier,
) {
    Surface(
        modifier =
            modifier,

        tonalElevation =
            6.dp,

        shape =
            MaterialTheme
                .shapes
                .large,
    ) {
        Box(
            modifier =
                Modifier.size(
                    56.dp
                ),

            contentAlignment =
                Alignment.Center,
        ) {
            /*
             * The complete North rose rotates around the center.
             * The degree label stays screen-aligned for readability.
             */
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .rotate(
                            presentation
                                .compassNorthRotationDegrees
                                .toFloat()
                        ),
            ) {
                Text(
                    text =
                        "N",

                    modifier =
                        Modifier
                            .align(
                                Alignment.TopCenter
                            )
                            .padding(
                                top =
                                    3.dp
                            ),

                    fontWeight =
                        FontWeight.Bold,

                    style =
                        MaterialTheme
                            .typography
                            .labelMedium,
                )

                Text(
                    text =
                        "▲",

                    modifier =
                        Modifier.align(
                            Alignment.Center
                        ),

                    color =
                        MaterialTheme
                            .colorScheme
                            .error,

                    fontWeight =
                        FontWeight.Bold,
                )
            }

            Text(
                text =
                    presentation
                        .label,

                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter
                        )
                        .padding(
                            bottom =
                                3.dp
                        ),

                style =
                    MaterialTheme
                        .typography
                        .labelSmall,
            )
        }
    }
}

private fun installNavigationLayers(
    style: Style,
    fullRoute: List<RoutePoint>,
    traveledRoute: List<RoutePoint>,
    remainingRoute: List<RoutePoint>,
    progressPosition: RoutePoint,
    displayPreferences: DisplayPreferences,
    observedPosition: NavigationObservedPositionPresentation?,
    selectedTarget: RoutePoint?,
) {
    style.addSource(
        GeoJsonSource(
            PREVIEW_ROUTE_SOURCE_ID,
            routeGeoJson(
                fullRoute
            ),
        )
    )

    style.addLayer(
        LineLayer(
            PREVIEW_ROUTE_LAYER_ID,
            PREVIEW_ROUTE_SOURCE_ID,
        ).withProperties(
            PropertyFactory.lineColor(
                "#0067A3"
            ),

            PropertyFactory.lineWidth(
                NavigationMapPresentation
                    .previewRouteLineWidth(
                        displayPreferences
                    )
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            REMAINING_ROUTE_SOURCE_ID,
            routeGeoJson(
                remainingRoute
            ),
        )
    )

    style.addLayer(
        LineLayer(
            REMAINING_ROUTE_LAYER_ID,
            REMAINING_ROUTE_SOURCE_ID,
        ).withProperties(
            PropertyFactory.lineColor(
                "#0067A3"
            ),

            PropertyFactory.lineWidth(
                NavigationMapPresentation
                    .activeRouteLineWidth(
                        displayPreferences
                    )
            ),

            PropertyFactory.visibility(
                Property.NONE
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            TRAVELED_ROUTE_SOURCE_ID,
            routeGeoJson(
                traveledRoute
            ),
        )
    )

    style.addLayer(
        LineLayer(
            TRAVELED_ROUTE_LAYER_ID,
            TRAVELED_ROUTE_SOURCE_ID,
        ).withProperties(
            PropertyFactory.lineColor(
                "#6B7280"
            ),

            PropertyFactory.lineWidth(
                NavigationMapPresentation
                    .activeRouteLineWidth(
                        displayPreferences
                    )
            ),

            PropertyFactory.visibility(
                Property.NONE
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            ROUTE_PROGRESS_SOURCE_ID,
            pointGeoJson(
                progressPosition
            ),
        )
    )

    style.addLayer(
        CircleLayer(
            ROUTE_PROGRESS_LAYER_ID,
            ROUTE_PROGRESS_SOURCE_ID,
        ).withProperties(
            PropertyFactory.circleColor(
                "#FFFFFF"
            ),

            PropertyFactory.circleRadius(
                7.0f
            ),

            PropertyFactory.circleStrokeColor(
                "#0067A3"
            ),

            PropertyFactory.circleStrokeWidth(
                3.0f
            ),

            PropertyFactory.visibility(
                Property.NONE
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            SELECTED_TARGET_SOURCE_ID,
            selectedTarget
                ?.let {
                    pointGeoJson(
                        it
                    )
                }
                ?: emptyFeatureCollectionGeoJson(),
        )
    )

    style.addLayer(
        CircleLayer(
            SELECTED_TARGET_LAYER_ID,
            SELECTED_TARGET_SOURCE_ID,
        ).withProperties(
            PropertyFactory.circleColor(
                "#D81B60"
            ),

            PropertyFactory.circleRadius(
                10.0f
            ),

            PropertyFactory.circleStrokeColor(
                "#FFFFFF"
            ),

            PropertyFactory.circleStrokeWidth(
                3.0f
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            OBSERVED_ACCURACY_SOURCE_ID,
            observedPosition
                ?.let {
                    observedAccuracyGeoJson(
                        it
                    )
                }
                ?: emptyFeatureCollectionGeoJson(),
        )
    )

    style.addLayer(
        FillLayer(
            OBSERVED_ACCURACY_LAYER_ID,
            OBSERVED_ACCURACY_SOURCE_ID,
        ).withProperties(
            PropertyFactory.fillColor(
                observedPosition
                    ?.markerColor
                    ?: "#6B7280"
            ),

            PropertyFactory.fillOpacity(
                0.18f
            ),

            PropertyFactory.visibility(
                if (
                    observedPosition
                        ?.accuracyRadiusM !=
                        null
                ) {
                    Property.VISIBLE
                } else {
                    Property.NONE
                }
            ),
        )
    )

    style.addSource(
        GeoJsonSource(
            OBSERVED_POSITION_SOURCE_ID,
            observedPosition
                ?.let {
                    pointGeoJson(
                        it.position
                    )
                }
                ?: emptyFeatureCollectionGeoJson(),
        )
    )

    style.addLayer(
        CircleLayer(
            OBSERVED_POSITION_LAYER_ID,
            OBSERVED_POSITION_SOURCE_ID,
        ).withProperties(
            PropertyFactory.circleColor(
                observedPosition
                    ?.markerColor
                    ?: "#6B7280"
            ),

            PropertyFactory.circleRadius(
                9.0f
            ),

            PropertyFactory.circleStrokeColor(
                "#FFFFFF"
            ),

            PropertyFactory.circleStrokeWidth(
                3.0f
            ),

            PropertyFactory.visibility(
                if (
                    observedPosition !=
                        null
                ) {
                    Property.VISIBLE
                } else {
                    Property.NONE
                }
            ),
        )
    )
}

private fun fitPreviewRoute(
    map: MapLibreMap,
    points: List<RoutePoint>,
) {
    if (points.size < 2) {
        return
    }

    val coordinates =
        points.map {
            LatLng(
                it.latitude,
                it.longitude,
            )
        }

    val bounds =
        LatLngBounds
            .Builder()
            .includes(
                coordinates
            )
            .build()

    val fitted =
        runCatching {
            map.moveCamera(
                CameraUpdateFactory
                    .newLatLngBounds(
                        bounds,
                        72,
                    )
            )
        }.isSuccess

    if (!fitted) {
        map.cameraPosition =
            CameraPosition
                .Builder()
                .target(
                    LatLng(
                        points
                            .map {
                                it.latitude
                            }
                            .average(),

                        points
                            .map {
                                it.longitude
                            }
                            .average(),
                    )
                )
                .zoom(
                    13.0
                )
                .bearing(
                    0.0
                )
                .tilt(
                    0.0
                )
                .build()
    }
}

private fun routeGeoJson(
    points: List<RoutePoint>,
): String {

    val coordinates =
        points.joinToString(
            separator = ",",
        ) {
            "[${it.longitude},${it.latitude}]"
        }

    return """
        {"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}]}
    """.trimIndent()
}

private fun pointGeoJson(
    point: RoutePoint,
): String =
    """
        {"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Point","coordinates":[${point.longitude},${point.latitude}]}}]}
    """.trimIndent()

private fun observedAccuracyGeoJson(
    presentation:
        NavigationObservedPositionPresentation,
): String {

    val radius =
        presentation
            .accuracyRadiusM
            ?: return emptyFeatureCollectionGeoJson()

    val boundary =
        NavigationObservedPositionPresentation
            .accuracyBoundary(
                center =
                    presentation.position,

                radiusM =
                    radius,
            )

    val coordinates =
        boundary.joinToString(
            separator = ",",
        ) {
            "[${it.longitude},${it.latitude}]"
        }

    return """
        {"type":"FeatureCollection","features":[{"type":"Feature","properties":{},"geometry":{"type":"Polygon","coordinates":[[$coordinates]]}}]}
    """.trimIndent()
}

private fun emptyFeatureCollectionGeoJson():
    String =
    """{"type":"FeatureCollection","features":[]}"""

private const val PREVIEW_ROUTE_SOURCE_ID =
    "routing-platform-preview-route-source"

private const val PREVIEW_ROUTE_LAYER_ID =
    "routing-platform-preview-route-layer"

private const val REMAINING_ROUTE_SOURCE_ID =
    "routing-platform-remaining-route-source"

private const val REMAINING_ROUTE_LAYER_ID =
    "routing-platform-remaining-route-layer"

private const val TRAVELED_ROUTE_SOURCE_ID =
    "routing-platform-traveled-route-source"

private const val TRAVELED_ROUTE_LAYER_ID =
    "routing-platform-traveled-route-layer"

private const val ROUTE_PROGRESS_SOURCE_ID =
    "routing-platform-route-progress-source"

private const val ROUTE_PROGRESS_LAYER_ID =
    "routing-platform-route-progress-layer"

private const val SELECTED_TARGET_SOURCE_ID =
    "routing-platform-selected-target-source"

private const val SELECTED_TARGET_LAYER_ID =
    "routing-platform-selected-target-layer"

private const val OBSERVED_ACCURACY_SOURCE_ID =
    "routing-platform-observed-accuracy-source"

private const val OBSERVED_ACCURACY_LAYER_ID =
    "routing-platform-observed-accuracy-layer"

private const val OBSERVED_POSITION_SOURCE_ID =
    "routing-platform-observed-position-source"

private const val OBSERVED_POSITION_LAYER_ID =
    "routing-platform-observed-position-layer"