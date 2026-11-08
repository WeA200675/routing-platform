package org.routingplatform.app.places

data class FuelPlanInput(
    val currentRangeMeters: Double,
    val routeDistanceMeters: Double,
    val reserveMeters: Double,
    val fuelStations: List<NavigationPoi>,
)

data class FuelPlanRecommendation(
    val stationId: String?,
    val requiredBeforeRoute: Boolean,
    val reason: String,
)

object FuelPlanning {
    fun recommend(input: FuelPlanInput): FuelPlanRecommendation {
        val reachable = input.fuelStations.filter {
            it.distanceMeters <= input.currentRangeMeters - input.reserveMeters
        }
        val station = reachable.minWithOrNull(
            compareBy<NavigationPoi> { it.detourSeconds ?: Int.MAX_VALUE }
                .thenBy { it.priceCents ?: Int.MAX_VALUE }
                .thenBy { it.distanceMeters },
        )
        return when {
            input.routeDistanceMeters <= input.currentRangeMeters - input.reserveMeters ->
                FuelPlanRecommendation(null, false, "Reichweite reicht bis zum Ziel")
            station != null ->
                FuelPlanRecommendation(station.id, true, "Tankstopp vor Erreichen der Reserve")
            else ->
                FuelPlanRecommendation(null, true, "Keine erreichbare Tankstelle im Datenbestand")
        }
    }
}
