package org.routingplatform.app.ui

import kotlin.math.abs

/**
 * Dampens small trusted-heading fluctuations before they reach MapLibre.
 *
 * This class only stabilizes presentation. It does not change the navigation
 * matcher, accepted progress, observed position, or reroute decisions.
 */
internal class NavigationBearingStabilizer(
    private val deadbandDegrees: Double = 2.5,
    private val smoothingFactor: Double = 0.35,
) {
    private var stableBearingDegrees: Double? = null

    init {
        require(deadbandDegrees.isFinite() && deadbandDegrees >= 0.0)
        require(smoothingFactor.isFinite() && smoothingFactor in 0.0..1.0)
    }

    fun update(targetBearingDegrees: Double): Double {
        require(targetBearingDegrees.isFinite())

        val target = normalize(targetBearingDegrees)
        val current = stableBearingDegrees
        if (current == null) {
            stableBearingDegrees = target
            return target
        }

        val delta = shortestSignedDelta(current, target)
        if (abs(delta) > deadbandDegrees) {
            stableBearingDegrees =
                normalize(current + delta * smoothingFactor)
        }

        return checkNotNull(stableBearingDegrees)
    }

    fun reset() {
        stableBearingDegrees = 0.0
    }

    private fun shortestSignedDelta(from: Double, to: Double): Double =
        ((to - from + 540.0) % 360.0) - 180.0

    private fun normalize(value: Double): Double {
        val remainder = value % 360.0
        return if (remainder < 0.0) remainder + 360.0 else remainder
    }
}
