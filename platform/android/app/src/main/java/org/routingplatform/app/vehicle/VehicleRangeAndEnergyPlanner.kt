package org.routingplatform.app.vehicle

import org.routingplatform.app.navigation.NavigationPoiCategory
import org.routingplatform.app.navigation.NavigationPoiRecommendation

data class VehicleProfile(
    val vehicleId: String,
    val energyType: VehicleEnergyType,
    val tankOrBatteryCapacity: Double,
    val baselineConsumptionPer100: Double,
    val safetyReservePercent: Double = 15.0,
    val active: Boolean = true,
) {
    init {
        require(vehicleId.isNotBlank())
        require(tankOrBatteryCapacity > 0.0)
        require(baselineConsumptionPer100 > 0.0)
        require(safetyReservePercent in 0.0..50.0)
    }
}

data class VehicleEnergyState(
    val levelPercent: Double,
    val estimatedConsumptionPer100: Double,
    val estimatedRangeKm: Double,
    val safeRangeKm: Double,
    val capturedAtEpochMs: Long,
)

object VehicleRangePlanner {
    fun estimate(
        profile: VehicleProfile,
        levelPercent: Double,
        calibratedConsumptionPer100: Double? = null,
        nowEpochMs: Long,
    ): VehicleEnergyState {
        val level = levelPercent.coerceIn(0.0, 100.0)
        val consumption = (calibratedConsumptionPer100 ?: profile.baselineConsumptionPer100)
            .coerceAtLeast(0.1)
        val availableEnergy = profile.tankOrBatteryCapacity * level / 100.0
        val range = availableEnergy / consumption * 100.0
        val safeRange = range * (1.0 - profile.safetyReservePercent / 100.0)
        return VehicleEnergyState(level, consumption, range, safeRange, nowEpochMs)
    }

    fun shouldPlanStop(state: VehicleEnergyState, routeRemainingKm: Double): Boolean =
        routeRemainingKm > state.safeRangeKm
}

enum class EnergyStopKind { FUEL, CHARGING }

data class EnergyStopCandidate(
    val poi: NavigationPoiRecommendation,
    val kind: EnergyStopKind,
    val estimatedCost: Double?,
    val available: Boolean?,
    val detourMeters: Double,
)

interface EnergyPriceAvailabilityProvider {
    fun lookup(
        poi: NavigationPoiRecommendation,
        kind: EnergyStopKind,
    ): EnergyPriceAvailability?
}

data class EnergyPriceAvailability(
    val pricePerUnit: Double?,
    val available: Boolean?,
    val capturedAtEpochMs: Long,
)

object EnergyStopPlanner {
    fun choose(
        candidates: List<NavigationPoiRecommendation>,
        profile: VehicleProfile,
        state: VehicleEnergyState,
        routeRemainingKm: Double,
        prices: EnergyPriceAvailabilityProvider? = null,
    ): EnergyStopCandidate? {
        if (!VehicleRangePlanner.shouldPlanStop(state, routeRemainingKm)) return null
        val expectedKind = if (profile.energyType == VehicleEnergyType.FUEL) {
            EnergyStopKind.FUEL
        } else {
            EnergyStopKind.CHARGING
        }
        return candidates
            .filter {
                it.category == if (expectedKind == EnergyStopKind.FUEL) {
                    NavigationPoiCategory.FUEL
                } else {
                    NavigationPoiCategory.CHARGING
                }
            }
            .map { poi ->
                val availability = prices?.lookup(poi, expectedKind)
                EnergyStopCandidate(
                    poi = poi,
                    kind = expectedKind,
                    estimatedCost = availability?.pricePerUnit,
                    available = availability?.available,
                    detourMeters = poi.distanceMeters,
                )
            }
            .filter { it.available != false }
            .sortedWith(
                compareBy<EnergyStopCandidate> { it.estimatedCost ?: Double.MAX_VALUE }
                    .thenBy { it.detourMeters },
            )
            .firstOrNull()
    }
}
