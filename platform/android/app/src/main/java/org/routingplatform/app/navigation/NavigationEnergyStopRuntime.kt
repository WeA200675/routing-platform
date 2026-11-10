package org.routingplatform.app.navigation

import org.routingplatform.app.vehicle.EnergyStopCandidate
import org.routingplatform.app.vehicle.VehicleEnergyStopCoordinator
import org.routingplatform.app.vehicle.VehicleMeasurement

class NavigationEnergyStopRuntime(
    private val coordinator: VehicleEnergyStopCoordinator,
    private val onRecommendation: (EnergyStopCandidate?) -> Unit,
    private val onUseAsViaPoint: (EnergyStopCandidate) -> Unit,
) {
    fun updateMeasurement(measurement: VehicleMeasurement) {
        coordinator.updateMeasurement(measurement)
    }

    fun update(
        measurement: VehicleMeasurement?,
        liveEvents: List<NavigationLiveEvent>,
        currentPosition: RoutePoint,
        routeRemainingKm: Double,
        nowEpochMs: Long,
    ) {
        measurement?.let(coordinator::updateMeasurement)
        val pois = NavigationPoiRecommendationEngine.recommend(
            events = liveEvents,
            currentPosition = currentPosition,
            nowEpochMs = nowEpochMs,
        )
        val candidate = coordinator.recommend(
            recommendations = pois,
            routeRemainingKm = routeRemainingKm.coerceAtLeast(0.0),
            nowEpochMs = nowEpochMs,
        )
        onRecommendation(candidate)
    }

    fun accept(candidate: EnergyStopCandidate?) {
        candidate?.let(onUseAsViaPoint)
    }
}
