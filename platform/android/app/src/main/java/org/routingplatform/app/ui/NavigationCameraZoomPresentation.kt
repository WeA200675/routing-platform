package org.routingplatform.app.ui

/*
 * G6.11 automatic camera zoom is presentation-only.
 *
 * The policy consumes an already accepted maneuver distance and a profile
 * display zoom. It cannot mutate route geometry, route progress, positioning,
 * rerouting, candidate selection, cost evaluation, or safety state.
 */
internal enum class NavigationCameraZoomStage {
    Fixed,
    Cruise,
    Approach,
    Near,
    Immediate,
}

internal data class NavigationCameraZoomPresentation(
    val stage:
        NavigationCameraZoomStage,

    val zoom:
        Double,

    val automaticApplied:
        Boolean,
) {
    init {
        require(
            zoom.isFinite() &&
                zoom in
                MIN_NAVIGATION_CAMERA_ZOOM..
                MAX_NAVIGATION_CAMERA_ZOOM
        ) {
            "zoom must be finite and inside the supported map range."
        }

        if (!automaticApplied) {
            require(
                stage ==
                    NavigationCameraZoomStage.Fixed
            ) {
                "Non-automatic camera zoom must use the Fixed stage."
            }
        }
    }
}

internal object NavigationCameraZoomPolicy {

    fun create(
        automaticEnabled:
            Boolean,

        defaultZoom:
            Double,

        distanceToManeuverM:
            Double,
    ): NavigationCameraZoomPresentation {
        require(
            defaultZoom.isFinite() &&
                defaultZoom in
                MIN_NAVIGATION_CAMERA_ZOOM..
                MAX_NAVIGATION_CAMERA_ZOOM
        ) {
            "defaultZoom must be finite and in [4, 22]."
        }

        if (
            !automaticEnabled ||
            !distanceToManeuverM.isFinite() ||
            distanceToManeuverM <
                0.0
        ) {
            return NavigationCameraZoomPresentation(
                stage =
                    NavigationCameraZoomStage.Fixed,

                zoom =
                    defaultZoom,

                automaticApplied =
                    false,
            )
        }

        val stage =
            when {
                distanceToManeuverM <=
                    IMMEDIATE_DISTANCE_M ->
                    NavigationCameraZoomStage.Immediate

                distanceToManeuverM <=
                    NEAR_DISTANCE_M ->
                    NavigationCameraZoomStage.Near

                distanceToManeuverM <=
                    APPROACH_DISTANCE_M ->
                    NavigationCameraZoomStage.Approach

                else ->
                    NavigationCameraZoomStage.Cruise
            }

        val offset =
            when (
                stage
            ) {
                NavigationCameraZoomStage.Fixed ->
                    0.0

                NavigationCameraZoomStage.Cruise ->
                    -0.75

                NavigationCameraZoomStage.Approach ->
                    0.0

                NavigationCameraZoomStage.Near ->
                    0.75

                NavigationCameraZoomStage.Immediate ->
                    1.50
            }

        return NavigationCameraZoomPresentation(
            stage =
                stage,

            zoom =
                (
                    defaultZoom +
                        offset
                ).coerceIn(
                    MIN_NAVIGATION_CAMERA_ZOOM,
                    MAX_NAVIGATION_CAMERA_ZOOM,
                ),

            automaticApplied =
                true,
        )
    }
}

internal const val APPROACH_DISTANCE_M =
    800.0

internal const val NEAR_DISTANCE_M =
    250.0

internal const val IMMEDIATE_DISTANCE_M =
    80.0

private const val MIN_NAVIGATION_CAMERA_ZOOM =
    4.0

private const val MAX_NAVIGATION_CAMERA_ZOOM =
    22.0
