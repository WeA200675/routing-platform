package org.routingplatform.app.ui

import kotlin.math.abs

/**
 * Smooths map rotation while retaining a bounded angular velocity.
 * The first trusted heading after startup/reset is applied immediately.
 */
internal class NavigationBearingStabilizer(
    private val deadbandDegrees: Double = 1.0,
    private val smoothingFactor: Double = 0.35,
    private val maximumDegreesPerSecond: Double = 90.0,
) {
    private var stableBearingDegrees: Double? = null
    private var lastUpdateElapsedRealtimeMs: Long? = null

    init {
        require(deadbandDegrees.isFinite() && deadbandDegrees >= 0.0)
        require(smoothingFactor.isFinite() && smoothingFactor in 0.0..1.0)
        require(maximumDegreesPerSecond.isFinite() && maximumDegreesPerSecond > 0.0)
    }

    fun update(targetBearingDegrees: Double, elapsedRealtimeMs: Long): Double {
        require(targetBearingDegrees.isFinite())
        require(elapsedRealtimeMs >= 0L)

        val target = normalize(targetBearingDegrees)
        val current = stableBearingDegrees
        if (current == null) {
            stableBearingDegrees = target
            lastUpdateElapsedRealtimeMs = elapsedRealtimeMs
            return target
        }

        val previousTime = lastUpdateElapsedRealtimeMs
        lastUpdateElapsedRealtimeMs = elapsedRealtimeMs
        val elapsedMs =
            previousTime
                ?.let { (elapsedRealtimeMs - it).coerceIn(0L, MAX_FILTER_INTERVAL_MS) }
                ?: 0L
        val delta = shortestSignedDelta(current, target)
        if (abs(delta) <= deadbandDegrees || elapsedMs == 0L) {
            return current
        }

        val smoothedStep = delta * smoothingFactor
        val maximumStep = maximumDegreesPerSecond * elapsedMs / 1000.0
        val boundedStep = smoothedStep.coerceIn(-maximumStep, maximumStep)
        stableBearingDegrees = normalize(current + boundedStep)
        return checkNotNull(stableBearingDegrees)
    }

    fun reset() {
        stableBearingDegrees = null
        lastUpdateElapsedRealtimeMs = null
    }

    private fun shortestSignedDelta(from: Double, to: Double): Double =
        ((to - from + 540.0) % 360.0) - 180.0

    private fun normalize(value: Double): Double {
        val remainder = value % 360.0
        return if (remainder < 0.0) remainder + 360.0 else remainder
    }
}

private const val MAX_FILTER_INTERVAL_MS = 1_000L
