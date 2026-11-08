package org.routingplatform.app.places

data class TripOption(
    val id: String,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val tollCents: Int = 0,
    val fuelCents: Int = 0,
    val detourSeconds: Int = 0,
    val safetyScore: Double = 1.0,
    val comfortScore: Double = 1.0,
)

data class TripOptimizationWeights(
    val time: Double = 1.0,
    val cost: Double = 1.0,
    val safety: Double = 3.0,
    val comfort: Double = 0.5,
)

object TripOptionOptimizer {
    fun choose(
        options: List<TripOption>,
        weights: TripOptimizationWeights = TripOptimizationWeights(),
    ): TripOption? =
        options.asSequence()
            .filter { it.safetyScore >= 0.70 }
            .minByOrNull { option ->
                option.durationSeconds * weights.time +
                    (option.tollCents + option.fuelCents) * weights.cost +
                    option.detourSeconds * weights.time +
                    (1.0 - option.safetyScore) * 10_000.0 * weights.safety +
                    (1.0 - option.comfortScore) * 1_000.0 * weights.comfort
            }
}
