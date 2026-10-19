package org.routingplatform.app.ui

/*
 * Presentation-only camera zoom policy. Speed can adjust camera framing
 * only when it comes from a fresh, safety-trusted location sample.
 */
internal enum class NavigationCameraZoomStage {
    Fixed,
    Cruise,
    Approach,
    Near,
    Immediate,
}

internal data class NavigationCameraZoomPresentation(
    val stage: NavigationCameraZoomStage,
    val zoom: Double,
    val automaticApplied: Boolean,
) {
    init {
        require(zoom.isFinite() && zoom in MIN_NAVIGATION_CAMERA_ZOOM..MAX_NAVIGATION_CAMERA_ZOOM)
        if (!automaticApplied) require(stage == NavigationCameraZoomStage.Fixed)
    }
}

internal object NavigationCameraZoomPolicy {
    fun create(
        automaticEnabled: Boolean,
        defaultZoom: Double,
        distanceToManeuverM: Double,
        speedMps: Double? = null,
        speedAccuracyMps: Double? = null,
        speedTrusted: Boolean = false,
        speedSampleAgeMs: Long? = null,
    ): NavigationCameraZoomPresentation {
        require(defaultZoom.isFinite() && defaultZoom in MIN_NAVIGATION_CAMERA_ZOOM..MAX_NAVIGATION_CAMERA_ZOOM)

        if (!automaticEnabled) {
            return NavigationCameraZoomPresentation(NavigationCameraZoomStage.Fixed, defaultZoom, false)
        }

        val speedIsFreshAndTrusted =
            speedTrusted &&
                speedMps != null &&
                speedMps.isFinite() &&
                speedMps in 0.0..MAX_SPEED_MPS &&
                (speedAccuracyMps == null ||
                    (speedAccuracyMps.isFinite() && speedAccuracyMps in 0.0..MAX_SPEED_ACCURACY_MPS)) &&
                speedSampleAgeMs != null &&
                speedSampleAgeMs in 0L..MAX_SPEED_SAMPLE_AGE_MS

        val maneuverDistanceValid = distanceToManeuverM.isFinite() && distanceToManeuverM >= 0.0
        if (!speedIsFreshAndTrusted && !maneuverDistanceValid) {
            return NavigationCameraZoomPresentation(NavigationCameraZoomStage.Fixed, defaultZoom, false)
        }

        val stage = if (maneuverDistanceValid) {
            when {
                distanceToManeuverM <= IMMEDIATE_DISTANCE_M -> NavigationCameraZoomStage.Immediate
                distanceToManeuverM <= NEAR_DISTANCE_M -> NavigationCameraZoomStage.Near
                distanceToManeuverM <= APPROACH_DISTANCE_M -> NavigationCameraZoomStage.Approach
                else -> NavigationCameraZoomStage.Cruise
            }
        } else {
            NavigationCameraZoomStage.Fixed
        }

        val maneuverOffset = when (stage) {
            NavigationCameraZoomStage.Fixed -> 0.0
            NavigationCameraZoomStage.Cruise -> -0.75
            NavigationCameraZoomStage.Approach -> 0.0
            NavigationCameraZoomStage.Near -> 0.75
            NavigationCameraZoomStage.Immediate -> 1.50
        }
        val speedOffset = if (speedIsFreshAndTrusted) speedZoomOffset(speedMps!! * 3.6) else 0.0

        return NavigationCameraZoomPresentation(
            stage = stage,
            zoom = (defaultZoom + maneuverOffset + speedOffset)
                .coerceIn(MIN_NAVIGATION_CAMERA_ZOOM, MAX_NAVIGATION_CAMERA_ZOOM),
            automaticApplied = true,
        )
    }

    /** Continuous, bounded framing curve; the map camera adds temporal smoothing. */
    private fun speedZoomOffset(speedKmh: Double): Double {
        val anchors = listOf(0.0 to 0.85, 30.0 to 0.45, 80.0 to -0.25, 130.0 to -1.0, 220.0 to -1.0)
        val upperIndex = anchors.indexOfFirst { speedKmh <= it.first }.coerceAtLeast(1)
        val (lowSpeed, lowOffset) = anchors[upperIndex - 1]
        val (highSpeed, highOffset) = anchors[upperIndex]
        val fraction = ((speedKmh - lowSpeed) / (highSpeed - lowSpeed)).coerceIn(0.0, 1.0)
        return lowOffset + (highOffset - lowOffset) * fraction
    }
}

internal class NavigationCameraZoomStabilizer(
    private val smoothingFactor: Double = 0.35,
    private val maximumZoomLevelsPerSecond: Double = 0.9,
) {
    private var stableZoom: Double? = null
    private var lastUpdateElapsedRealtimeMs: Long? = null

    init {
        require(smoothingFactor.isFinite() && smoothingFactor in 0.0..1.0)
        require(maximumZoomLevelsPerSecond.isFinite() && maximumZoomLevelsPerSecond > 0.0)
    }

    fun update(targetZoom: Double, elapsedRealtimeMs: Long): Double {
        require(targetZoom.isFinite() && targetZoom in MIN_NAVIGATION_CAMERA_ZOOM..MAX_NAVIGATION_CAMERA_ZOOM)
        require(elapsedRealtimeMs >= 0L)
        val current = stableZoom
        if (current == null) {
            stableZoom = targetZoom
            lastUpdateElapsedRealtimeMs = elapsedRealtimeMs
            return targetZoom
        }
        val previous = lastUpdateElapsedRealtimeMs
        lastUpdateElapsedRealtimeMs = elapsedRealtimeMs
        val elapsed = previous?.let { (elapsedRealtimeMs - it).coerceIn(0L, MAX_FILTER_INTERVAL_MS) } ?: 0L
        if (elapsed == 0L) return current
        val delta = (targetZoom - current) * smoothingFactor
        val limit = maximumZoomLevelsPerSecond * elapsed / 1000.0
        stableZoom = (current + delta.coerceIn(-limit, limit))
            .coerceIn(MIN_NAVIGATION_CAMERA_ZOOM, MAX_NAVIGATION_CAMERA_ZOOM)
        return checkNotNull(stableZoom)
    }

    fun reset() {
        stableZoom = null
        lastUpdateElapsedRealtimeMs = null
    }
}

internal const val APPROACH_DISTANCE_M = 800.0
internal const val NEAR_DISTANCE_M = 250.0
internal const val IMMEDIATE_DISTANCE_M = 80.0
private const val MAX_SPEED_MPS = 75.0
private const val MAX_SPEED_ACCURACY_MPS = 6.0
private const val MAX_SPEED_SAMPLE_AGE_MS = 5_000L
private const val MAX_FILTER_INTERVAL_MS = 1_000L
private const val MIN_NAVIGATION_CAMERA_ZOOM = 4.0
private const val MAX_NAVIGATION_CAMERA_ZOOM = 22.0
