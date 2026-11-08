package org.routingplatform.app.places

data class FuelPriceQuote(
    val stationId: String,
    val centsPerLiter: Int,
    val capturedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val source: String,
)

data class FuelStopCandidate(
    val station: NavigationPoi,
    val quote: FuelPriceQuote?,
    val expectedFuelLiters: Double,
    val reserveAfterStopMeters: Double,
)

object FuelStopOptimizer {
    fun choose(
        candidates: List<FuelStopCandidate>,
        nowEpochMs: Long,
    ): FuelStopCandidate? =
        candidates.asSequence()
            .filter { it.station.distanceMeters >= 0.0 }
            .filter { it.reserveAfterStopMeters >= 0.0 }
            .filter {
                val quote = it.quote
                quote == null || quote.validUntilEpochMs >= nowEpochMs
            }
            .minWithOrNull(
                compareBy<FuelStopCandidate> { it.station.detourSeconds ?: Int.MAX_VALUE }
                    .thenBy { it.quote?.centsPerLiter ?: Int.MAX_VALUE }
                    .thenBy { it.station.distanceMeters },
            )
}
