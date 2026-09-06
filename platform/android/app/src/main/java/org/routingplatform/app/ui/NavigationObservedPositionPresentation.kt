package org.routingplatform.app.ui

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.routingplatform.app.navigation.NavigationFusionMode
import org.routingplatform.app.navigation.NavigationPositionConfidence
import org.routingplatform.app.navigation.NavigationRouteProgressSafetyStatus
import org.routingplatform.app.navigation.RoutePoint

/*
 * Presentation-only view of the last observed device position.
 *
 * This object cannot accept route progress, invoke matching, change
 * native state or bypass a safety decision.
 */
data class NavigationObservedPositionPresentation(
    val position:
        RoutePoint,

    val accuracyM:
        Double?,

    val accuracyRadiusM:
        Double?,

    val confidence:
        NavigationPositionConfidence,

    val safetyStatus:
        NavigationRouteProgressSafetyStatus?,

    val fusionMode:
        NavigationFusionMode?,

    val markerColor:
        String,

    val cameraFollowAllowed:
        Boolean,

    val statusText:
        String,
) {
    companion object {

        fun create(
            position:
                RoutePoint?,

            accuracyM:
                Double?,

            confidence:
                NavigationPositionConfidence,

            safetyStatus:
                NavigationRouteProgressSafetyStatus?,

            fusionMode:
                NavigationFusionMode?,
        ): NavigationObservedPositionPresentation? {

            val observed =
                position
                    ?: return null

            require(
                observed.latitude.isFinite() &&
                    observed.latitude in
                        -90.0..90.0
            )

            require(
                observed.longitude.isFinite() &&
                    observed.longitude in
                        -180.0..180.0
            )

            val normalizedAccuracy =
                accuracyM
                    ?.takeIf {
                        it.isFinite() &&
                            it >= 0.0
                    }

            /*
             * Very large uncertainty is still shown in text, but
             * deliberately not rendered as a huge map polygon.
             */
            val accuracyRadius =
                normalizedAccuracy
                    ?.takeIf {
                        it in
                            MIN_ACCURACY_RADIUS_M..
                                MAX_ACCURACY_RADIUS_M
                    }

            val trustedForCamera =
                safetyStatus ==
                    NavigationRouteProgressSafetyStatus.Accepted &&
                    (
                        confidence ==
                            NavigationPositionConfidence.High ||
                            confidence ==
                                NavigationPositionConfidence.Medium
                    ) &&
                    fusionMode ==
                        NavigationFusionMode.DirectObservation

            val markerColor =
                when {
                    trustedForCamera ->
                        TRUSTED_MARKER_COLOR

                    confidence ==
                        NavigationPositionConfidence.Low ||
                        confidence ==
                            NavigationPositionConfidence.Lost ->
                        LOW_CONFIDENCE_MARKER_COLOR

                    else ->
                        HELD_MARKER_COLOR
                }

            val accuracyText =
                normalizedAccuracy
                    ?.roundToInt()
                    ?.let {
                        "$it m"
                    }
                    ?: "Genauigkeit unbekannt"

            val confidenceText =
                when (confidence) {
                    NavigationPositionConfidence.High ->
                        "Hoch"

                    NavigationPositionConfidence.Medium ->
                        "Mittel"

                    NavigationPositionConfidence.Low ->
                        "Niedrig"

                    NavigationPositionConfidence.Lost ->
                        "Verloren"
                }

            val safetyText =
                when (safetyStatus) {
                    NavigationRouteProgressSafetyStatus.Accepted ->
                        "Safety: akzeptiert"

                    null ->
                        "Safety: prüft"

                    else ->
                        "Safety: Hold"
                }

            return NavigationObservedPositionPresentation(
                position =
                    observed,

                accuracyM =
                    normalizedAccuracy,

                accuracyRadiusM =
                    accuracyRadius,

                confidence =
                    confidence,

                safetyStatus =
                    safetyStatus,

                fusionMode =
                    fusionMode,

                markerColor =
                    markerColor,

                cameraFollowAllowed =
                    trustedForCamera,

                statusText =
                    "Position: beobachtet · " +
                        "$confidenceText · " +
                        "$accuracyText · " +
                        safetyText,
            )
        }

        /*
         * Geodesic small-circle approximation used only for visual
         * accuracy presentation. It does not feed positioning.
         */
        fun accuracyBoundary(
            center:
                RoutePoint,

            radiusM:
                Double,

            segments:
                Int =
                DEFAULT_ACCURACY_SEGMENTS,
        ): List<RoutePoint> {

            require(
                radiusM.isFinite() &&
                    radiusM > 0.0
            )

            require(
                segments in 12..180
            )

            val latitudeRadians =
                Math.toRadians(
                    center.latitude
                )

            val cosineLatitude =
                cos(
                    latitudeRadians
                ).coerceAtLeast(
                    1.0e-6
                )

            val angularRadius =
                radiusM /
                    EARTH_RADIUS_M

            return (
                0..segments
            ).map {
                    index ->

                val angle =
                    2.0 *
                        PI *
                        index.toDouble() /
                        segments.toDouble()

                val latitude =
                    center.latitude +
                        Math.toDegrees(
                            angularRadius *
                                cos(
                                    angle
                                )
                        )

                val longitude =
                    center.longitude +
                        Math.toDegrees(
                            angularRadius *
                                sin(
                                    angle
                                ) /
                                cosineLatitude
                        )

                RoutePoint(
                    latitude =
                        latitude.coerceIn(
                            -90.0,
                            90.0,
                        ),

                    longitude =
                        normalizeLongitude(
                            longitude
                        ),
                )
            }
        }

        private fun normalizeLongitude(
            degrees:
                Double,
        ): Double {
            var result =
                degrees

            while (result > 180.0) {
                result -=
                    360.0
            }

            while (result < -180.0) {
                result +=
                    360.0
            }

            return result
        }
    }
}

private const val EARTH_RADIUS_M =
    6_371_000.0

private const val MIN_ACCURACY_RADIUS_M =
    0.5

private const val MAX_ACCURACY_RADIUS_M =
    1_000.0

private const val DEFAULT_ACCURACY_SEGMENTS =
    48

private const val TRUSTED_MARKER_COLOR =
    "#1565C0"

private const val HELD_MARKER_COLOR =
    "#D97706"

private const val LOW_CONFIDENCE_MARKER_COLOR =
    "#6B7280"