package org.routingplatform.app.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.LiveEventKind
import org.routingplatform.app.navigation.NavigationLiveEvent
import org.routingplatform.app.navigation.NavigationPoiCategory
import org.routingplatform.app.navigation.NavigationPoiRecommendation

class VehicleEnergyStopCoordinatorTest {
    @Test
    fun recommendsFuelStopWhenRouteExceedsSafeRange() {
        val profile = VehicleProfile("test", VehicleEnergyType.FUEL, 50.0, 7.0, 15.0)
        val coordinator = VehicleEnergyStopCoordinator(profile)
        coordinator.updateMeasurement(
            VehicleMeasurement(
                capturedAtEpochMs = 1_000,
                source = VehicleMeasurementSource.MANUAL,
                energyType = VehicleEnergyType.FUEL,
                odometerKm = null,
                consumptionPer100 = 7.0,
                remainingRangeKm = null,
                fuelLevelPercent = 20.0,
                batteryPercent = null,
                confidence = 1.0,
            ),
        )
        val poi = NavigationPoiRecommendation(
            event = NavigationLiveEvent(
                id = "fuel",
                kind = LiveEventKind.POI_UPDATE,
                latitude = 48.0,
                longitude = 11.0,
                capturedAtEpochMs = 1_000,
                validUntilEpochMs = 100_000,
                source = "osm-overpass:fuel:Test",
            ),
            category = NavigationPoiCategory.FUEL,
            title = "Test",
            distanceMeters = 500.0,
        )
        val result = coordinator.recommend(listOf(poi), 600.0, 2_000)
        assertEquals("Test", result?.poi?.title)
        assertTrue((coordinator.currentState(2_000)?.safeRangeKm ?: 0.0) < 200.0)
    }
}
