package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraZoomPresentationTest {
    @Test fun disabledAutomaticZoomKeepsProfileZoomExactly() {
        val result = NavigationCameraZoomPolicy.create(false, 16.0, 40.0, speedMps = 20.0, speedTrusted = true, speedSampleAgeMs = 0L)
        assertEquals(NavigationCameraZoomStage.Fixed, result.stage)
        assertEquals(16.0, result.zoom, 0.0001)
        assertFalse(result.automaticApplied)
    }

    @Test fun invalidManeuverAndMissingSpeedFailsClosedToFixedZoom() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { distance ->
            val result = NavigationCameraZoomPolicy.create(true, 16.0, distance)
            assertEquals(NavigationCameraZoomStage.Fixed, result.stage)
            assertEquals(16.0, result.zoom, 0.0001)
            assertFalse(result.automaticApplied)
        }
    }

    @Test fun maneuverStagesWidenCruiseAndTightenNearManeuver() {
        val cruise = NavigationCameraZoomPolicy.create(true, 16.0, 1200.0)
        val approach = NavigationCameraZoomPolicy.create(true, 16.0, 800.0)
        val near = NavigationCameraZoomPolicy.create(true, 16.0, 250.0)
        val immediate = NavigationCameraZoomPolicy.create(true, 16.0, 80.0)
        assertEquals(NavigationCameraZoomStage.Cruise, cruise.stage)
        assertEquals(NavigationCameraZoomStage.Approach, approach.stage)
        assertEquals(NavigationCameraZoomStage.Near, near.stage)
        assertEquals(NavigationCameraZoomStage.Immediate, immediate.stage)
        assertEquals(15.25, cruise.zoom, 0.0001)
        assertEquals(16.0, approach.zoom, 0.0001)
        assertEquals(16.75, near.zoom, 0.0001)
        assertEquals(17.5, immediate.zoom, 0.0001)
    }

    @Test fun speedContinuouslyWidensAtLowSpeedAndShowsMoreRoadAtHighSpeed() {
        fun zoom(speedMps: Double) = NavigationCameraZoomPolicy.create(
            automaticEnabled = true,
            defaultZoom = 16.0,
            distanceToManeuverM = Double.POSITIVE_INFINITY,
            speedMps = speedMps,
            speedAccuracyMps = 1.0,
            speedTrusted = true,
            speedSampleAgeMs = 100L,
        ).zoom
        val walking = zoom(0.0)
        val town = zoom(8.0)
        val highway = zoom(36.0)
        assertTrue(walking > town)
        assertTrue(town > highway)
        assertTrue(walking - highway > 1.0)
    }

    @Test fun staleUntrustedOrImplausibleSpeedCannotChangeZoom() {
        val baseline = NavigationCameraZoomPolicy.create(true, 16.0, Double.POSITIVE_INFINITY)
        val stale = NavigationCameraZoomPolicy.create(true, 16.0, Double.POSITIVE_INFINITY, 30.0, 1.0, true, 5_001L)
        val untrusted = NavigationCameraZoomPolicy.create(true, 16.0, Double.POSITIVE_INFINITY, 30.0, 1.0, false, 100L)
        val inaccurate = NavigationCameraZoomPolicy.create(true, 16.0, Double.POSITIVE_INFINITY, 30.0, 10.0, true, 100L)
        assertEquals(16.0, baseline.zoom, 0.0001)
        assertFalse(stale.automaticApplied)
        assertFalse(untrusted.automaticApplied)
        assertFalse(inaccurate.automaticApplied)
    }

    @Test fun speedAndManeuverOffsetsStayWithinMapZoomBounds() {
        val minimum = NavigationCameraZoomPolicy.create(true, 4.0, 5000.0, 70.0, 1.0, true, 0L)
        val maximum = NavigationCameraZoomPolicy.create(true, 22.0, 10.0, 0.0, 1.0, true, 0L)
        assertEquals(4.0, minimum.zoom, 0.0001)
        assertEquals(22.0, maximum.zoom, 0.0001)
    }

    @Test fun zoomStabilizerBoundsChangesAndResetAllowsFreshStart() {
        val stabilizer = NavigationCameraZoomStabilizer()
        assertEquals(16.0, stabilizer.update(16.0, 1000L), 0.0001)
        val changed = stabilizer.update(12.0, 2000L)
        assertTrue(15.0 < changed)
        assertTrue(changed >= 15.1)
        stabilizer.reset()
        assertEquals(12.0, stabilizer.update(12.0, 3000L), 0.0001)
    }
}
