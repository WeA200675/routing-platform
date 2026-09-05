package org.routingplatform.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.routingplatform.app.navigation.RoutePoint

@Composable
fun RouteMap(
    points: List<RoutePoint>,
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

            mapView.onDestroy()
        }
    }

    LaunchedEffect(
        mapView,
        points,
    ) {
        if (points.size < 2) {
            return@LaunchedEffect
        }

        val geoJson =
            routeGeoJson(points)

        val center =
            LatLng(
                points
                    .map { it.latitude }
                    .average(),

                points
                    .map { it.longitude }
                    .average(),
            )

        mapView.getMapAsync { map ->
            map.setStyle(STYLE_URL) { style ->
                style.addSource(
                    GeoJsonSource(
                        ROUTE_SOURCE_ID,
                        geoJson,
                    )
                )

                style.addLayer(
                    LineLayer(
                        ROUTE_LAYER_ID,
                        ROUTE_SOURCE_ID,
                    ).withProperties(
                        PropertyFactory.lineColor(
                            "#0067A3"
                        ),

                        PropertyFactory.lineWidth(
                            6.0f
                        ),
                    )
                )

                map.cameraPosition =
                    CameraPosition
                        .Builder()
                        .target(center)
                        .zoom(13.2)
                        .build()
            }
        }
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

    return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

private const val STYLE_URL =
    "https://demotiles.maplibre.org/style.json"

private const val ROUTE_SOURCE_ID =
    "routing-platform-route-source"

private const val ROUTE_LAYER_ID =
    "routing-platform-route-layer"
