package org.routingplatform.app.vehicle

import org.routingplatform.app.navigation.NavigationPoiRecommendation

class VehicleEnergyStopCoordinator(
    private val profile: VehicleProfile,
    private val priceProvider: EnergyPriceAvailabilityProvider? = null,
) {
    private var latestMeasurement: VehicleMeasurement? = null

    fun updateMeasurement(measurement: VehicleMeasurement) {
        if (measurement.energyType != profile.energyType) return
        if (measurement.confidence < 0.70) return
        latestMeasurement = measurement
    }

    fun recommend(
        recommendations: List<NavigationPoiRecommendation>,
        routeRemainingKm: Double,
        nowEpochMs: Long,
    ): EnergyStopCandidate? {
        val measurement = latestMeasurement ?: return null
        val level = measurement.batteryPercent ?: measurement.fuelLevelPercent ?: return null
        val state = VehicleRangePlanner.estimate(
            profile = profile,
            levelPercent = level,
            calibratedConsumptionPer100 = measurement.consumptionPer100,
            nowEpochMs = nowEpochMs,
        )
        return EnergyStopPlanner.choose(
            candidates = recommendations,
            profile = profile,
            state = state,
            routeRemainingKm = routeRemainingKm,
            prices = priceProvider,
        )
    }

    fun currentState(nowEpochMs: Long): VehicleEnergyState? {
        val measurement = latestMeasurement ?: return null
        val level = measurement.batteryPercent ?: measurement.fuelLevelPercent ?: return null
        return VehicleRangePlanner.estimate(
            profile = profile,
            levelPercent = level,
            calibratedConsumptionPer100 = measurement.consumptionPer100,
            nowEpochMs = nowEpochMs,
        )
    }
}
