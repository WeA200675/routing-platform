package org.routingplatform.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
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

@Composable
fun RouteMap(
    points: List<RoutePoint>,
    shapeSegmentIndex: Int,
    segmentFraction: Double,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

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

    DisposableEffect(
        lifecycleOwner,
        mapView,
    ) {
        val observer =
            LifecycleEventObserver { _, event ->
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

        lifecycleOwner.lifecycle.addObserver(
            observer
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(
                observer
            )

            loadedStyle =
                null

            mapLibreMap =
                null

            mapView.onDestroy()
        }
    }

    LaunchedEffect(
        mapView
    ) {
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

        mapView.getMapAsync { map ->
            mapLibreMap =
                map

            map.setStyle(
                STYLE_URL
            ) { style ->
                style.addSource(
                    GeoJsonSource(
                        PREVIEW_ROUTE_SOURCE_ID,
                        routeGeoJson(
                            points
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
                            6.0f
                        ),
                    )
                )

                style.addSource(
                    GeoJsonSource(
                        REMAINING_ROUTE_SOURCE_ID,
                        routeGeoJson(
                            progressGeometry
                                .remainingPoints
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
                            7.0f
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
                            progressGeometry
                                .traveledPoints
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
                            7.0f
                        ),

                        PropertyFactory.visibility(
                            Property.NONE
                        ),
                    )
                )

                style.addSource(
                    GeoJsonSource(
                        CURRENT_POSITION_SOURCE_ID,
                        pointGeoJson(
                            progressGeometry
                                .currentPosition
                        ),
                    )
                )

                style.addLayer(
                    CircleLayer(
                        CURRENT_POSITION_LAYER_ID,
                        CURRENT_POSITION_SOURCE_ID,
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

                loadedStyle =
                    style
            }
        }
    }

    LaunchedEffect(
        loadedStyle,
        mapLibreMap,
        points,
        shapeSegmentIndex,
        segmentFraction,
        showProgress,
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
                CURRENT_POSITION_SOURCE_ID
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
                )
            )

        style
            .getLayer(
                REMAINING_ROUTE_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                )
            )

        style
            .getLayer(
                TRAVELED_ROUTE_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                )
            )

        style
            .getLayer(
                CURRENT_POSITION_LAYER_ID
            )
            ?.setProperties(
                PropertyFactory.visibility(
                    progressVisibility
                )
            )

        val target =
            if (showProgress) {
                LatLng(
                    progressGeometry
                        .currentPosition
                        .latitude,

                    progressGeometry
                        .currentPosition
                        .longitude,
                )
            } else {
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
            }

        map.cameraPosition =
            CameraPosition
                .Builder()
                .target(
                    target
                )
                .zoom(
                    if (showProgress) {
                        14.5
                    } else {
                        13.2
                    }
                )
                .build()
    }

    AndroidView(
        factory = {
            mapView
        },
        modifier = modifier,
    )
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

private const val STYLE_URL =
    "https://demotiles.maplibre.org/style.json"

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

private const val CURRENT_POSITION_SOURCE_ID =
    "routing-platform-current-position-source"

private const val CURRENT_POSITION_LAYER_ID =
    "routing-platform-current-position-layer"