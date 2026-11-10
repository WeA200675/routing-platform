package org.routingplatform.app.navigation

import org.routingplatform.app.vehicle.EnergyPriceProviderFactory
import org.routingplatform.app.vehicle.VehicleEnergyStopCoordinator
import org.routingplatform.app.vehicle.VehicleProfile

object VehicleEnergyStopRuntimeFactory {
    fun create(
        profile: VehicleProfile,
        onRecommendation: (org.routingplatform.app.vehicle.EnergyStopCandidate?) -> Unit,
        onUseAsViaPoint: (org.routingplatform.app.vehicle.EnergyStopCandidate) -> Unit,
        priceProvider: org.routingplatform.app.vehicle.EnergyPriceAvailabilityProvider? = null,
    ): NavigationEnergyStopRuntime =
        NavigationEnergyStopRuntime(
            coordinator = VehicleEnergyStopCoordinator(
                profile = profile,
                priceProvider = priceProvider,
            ),
            onRecommendation = onRecommendation,
            onUseAsViaPoint = onUseAsViaPoint,
        )
}
