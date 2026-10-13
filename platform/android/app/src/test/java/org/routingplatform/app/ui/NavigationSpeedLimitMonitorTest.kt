package org.routingplatform.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationSpeedLimitDataStatus
import org.routingplatform.app.navigation.NavigationSpeedLimitSegment
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint

class NavigationSpeedLimitMonitorTest {
    @Test
    fun warningRequiresTwoDistinctFreshTrustedFixes() {
        val monitor = NavigationSpeedLimitMonitor()
        val snapshot = snapshot()
        val first = monitor.update(
            snapshot = snapshot,
            speedMps = 60.0 / 3.6,
            speedAccuracyMps = 0.5,
            speedSampleElapsedRealtimeNanos = 1_000_000_000L,
            speedPositionTrusted = true,
            nowElapsedRealtimeNanos = 1_100_000_000L,
            speedingThresholdKmh = 5,
        )
        assertFalse(first.presentation.overLimit)
        assertNull(first.warning)

        val repeatedSameFix = monitor.update(
            snapshot = snapshot,
            speedMps = 60.0 / 3.6,
            speedAccuracyMps = 0.5,
            speedSampleElapsedRealtimeNanos = 1_000_000_000L,
            speedPositionTrusted = true,
            nowElapsedRealtimeNanos = 1_200_000_000L,
            speedingThresholdKmh = 5,
        )
        assertFalse(repeatedSameFix.presentation.overLimit)
        assertNull(repeatedSameFix.warning)

        val secondDistinctFix = monitor.update(
            snapshot = snapshot,
            speedMps = 60.0 / 3.6,
            speedAccuracyMps = 0.5,
            speedSampleElapsedRealtimeNanos = 2_000_000_000L,
            speedPositionTrusted = true,
            nowElapsedRealtimeNanos = 2_100_000_000L,
            speedingThresholdKmh = 5,
        )
        assertTrue(secondDistinctFix.presentation.overLimit)
        assertNotNull(secondDistinctFix.warning)
    }

    @Test
    fun selectedThresholdControlsWarningBoundary() {
        val monitor = NavigationSpeedLimitMonitor()
        val snapshot = snapshot()
        val first = monitor.update(
            snapshot, 55.0 / 3.6, 0.5, 1_000_000_000L, true, 1_100_000_000L, 5,
        )
        assertFalse(first.presentation.overLimit)

        val second = monitor.update(
            snapshot, 56.0 / 3.6, 0.5, 2_000_000_000L, true, 2_100_000_000L, 5,
        )
        assertTrue(second.presentation.overLimit)
        assertNotNull(second.warning)
    }

    @Test
    fun staleOrUntrustedFixCannotTriggerWarning() {
        val snapshot = snapshot()
        val staleMonitor = NavigationSpeedLimitMonitor()
        val stale = staleMonitor.update(
            snapshot, 80.0 / 3.6, 0.5, 1L, true, 10_000_000_000L, 0,
        )
        assertFalse(stale.presentation.overLimit)
        assertNull(stale.warning)

        val untrustedMonitor = NavigationSpeedLimitMonitor()
        val untrusted = untrustedMonitor.update(
            snapshot, 80.0 / 3.6, 0.5, 1_000_000_000L, false, 1_100_000_000L, 0,
        )
        assertFalse(untrusted.presentation.overLimit)
        assertNull(untrusted.warning)
    }

    @Test
    fun zeroThresholdWarnsOnlyAboveTheMappedLimit() {
        val monitor = NavigationSpeedLimitMonitor()
        val snapshot = snapshot()
        val atLimit = monitor.update(
            snapshot, 50.0 / 3.6, 0.5, 1_000_000_000L, true, 1_100_000_000L, 0,
        )
        assertFalse(atLimit.presentation.overLimit)

        val aboveLimit = monitor.update(
            snapshot, 50.6 / 3.6, 0.5, 2_000_000_000L, true, 2_100_000_000L, 0,
        )
        assertTrue(aboveLimit.presentation.overLimit)
        assertNotNull(aboveLimit.warning)
    }

    private fun snapshot() = NavigationUiSnapshot(
        sessionId = "speed-test-session",
        state = NavigationSessionState.Navigating,
        routeId = "speed-test-route",
        routeDistanceM = 100.0,
        routeDurationS = 20.0,
        geometry = listOf(
            RoutePoint(47.0, 9.0),
            RoutePoint(47.001, 9.0),
        ),
        progressFraction = 0.1,
        remainingDistanceM = 90.0,
        remainingDurationS = 18.0,
        currentManeuver = null,
        nextManeuver = null,
        distanceToCurrentManeuverEndM = 50.0,
        arrived = false,
        speedLimitSegments = listOf(
            NavigationSpeedLimitSegment(
                beginShapeIndex = 0,
                endShapeIndex = 1,
                speedLimitKmh = 50.0,
            ),
        ),
        speedLimitDataStatus = NavigationSpeedLimitDataStatus.Complete,
    )
}
