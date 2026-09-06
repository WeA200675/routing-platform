package org.routingplatform.app.ui

import kotlin.math.roundToInt
import org.routingplatform.app.profile.ProfileMapOrientation

/*
 * Presentation-only compass / map-orientation contract.
 *
 * trustedTravelBearingDegrees is already filtered by the positioning
 * pipeline before it reaches this object.
 *
 * Even a trusted travel bearing must NOT rotate the map while the
 * existing G3 observed-position camera gate is closed.
 */
data class NavigationCompassPresentation(
    val trustedTravelBearingDegrees:
        Double?,

    val mapBearingDegrees:
        Double,

    val compassNorthRotationDegrees:
        Double,

    val headingUpActive:
        Boolean,

    val label:
        String,
) {
    companion object {

        fun create(
            mapOrientation:
                ProfileMapOrientation,

            trustedTravelBearingDegrees:
                Double?,

            cameraFollowAllowed:
                Boolean,
        ): NavigationCompassPresentation {

            val trustedBearing =
                trustedTravelBearingDegrees
                    ?.takeIf {
                        it.isFinite()
                    }
                    ?.let {
                        normalizeBearing(
                            it
                        )
                    }

            val headingUp =
                when (
                    mapOrientation
                ) {
                    ProfileMapOrientation.HeadingUp ->
                        cameraFollowAllowed &&
                            trustedBearing !=
                                null

                    ProfileMapOrientation.NorthUp ->
                        false
                }

            val mapBearing =
                if (headingUp) {
                    checkNotNull(
                        trustedBearing
                    )
                } else {
                    0.0
                }

            /*
             * MapLibre rotates the map by mapBearingDegrees.
             * The North indicator therefore rotates by the inverse
             * angle so it continues to point toward geographic north
             * on the screen.
             */
            val compassNorthRotation =
                normalizeSignedDegrees(
                    -mapBearing
                )

            val label =
                trustedBearing
                    ?.roundToInt()
                    ?.let {
                        "$it°"
                    }
                    ?: "N"

            return NavigationCompassPresentation(
                trustedTravelBearingDegrees =
                    trustedBearing,

                mapBearingDegrees =
                    mapBearing,

                compassNorthRotationDegrees =
                    compassNorthRotation,

                headingUpActive =
                    headingUp,

                label =
                    label,
            )
        }

        private fun normalizeBearing(
            degrees:
                Double,
        ): Double {

            val remainder =
                degrees %
                    FULL_TURN_DEGREES

            return if (
                remainder < 0.0
            ) {
                remainder +
                    FULL_TURN_DEGREES
            } else {
                remainder
            }
        }

        private fun normalizeSignedDegrees(
            degrees:
                Double,
        ): Double {

            val normalized =
                normalizeBearing(
                    degrees
                )

            return if (
                normalized >
                    HALF_TURN_DEGREES
            ) {
                normalized -
                    FULL_TURN_DEGREES
            } else {
                normalized
            }
        }
    }
}

private const val FULL_TURN_DEGREES =
    360.0

private const val HALF_TURN_DEGREES =
    180.0