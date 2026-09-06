package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.navigation.splitRouteProgressGeometry
import org.routingplatform.app.profile.DisplayPreferences

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
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val styleDescriptor =
        NavigationMapPresentation
            .styleDescriptor(
                displayPreferences
                    .mapStyle
            )

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
                    _ ->

                if (
                    loadedStyle ==
                        null
                ) {
                    mapLoadState =
                        NavigationMapLoadState
                            .Failed
                }
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

    /*
     * Style changes are presentation changes only.
     *
     * A future profile switch can therefore replace the street
     * appearance without replacing the native navigation session.
     */
    LaunchedEffect(
        mapView,
        styleDescriptor.styleUri,
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
                )

                loadedStyle =
                    style

                mapLoadState =
                    NavigationMapLoadState
                        .Rendering
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

        if (showProgress) {
            /*
             * Until G3 supplies a trusted heading, we deliberately
             * keep bearing at north-up instead of inventing one.
             */
            map.cameraPosition =
                CameraPosition
                    .Builder()
                    .target(
                        LatLng(
                            progressGeometry
                                .currentPosition
                                .latitude,

                            progressGeometry
                                .currentPosition
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

private fun installNavigationLayers(
    style: Style,
    fullRoute: List<RoutePoint>,
    traveledRoute: List<RoutePoint>,
    remainingRoute: List<RoutePoint>,
    progressPosition: RoutePoint,
    displayPreferences: DisplayPreferences,
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