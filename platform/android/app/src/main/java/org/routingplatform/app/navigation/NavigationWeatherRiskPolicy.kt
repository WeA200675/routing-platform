package org.routingplatform.app.navigation

enum class WeatherRiskKind {
    NONE,
    HEAVY_RAIN,
    ICE,
    SNOW,
    FOG,
    STRONG_WIND,
    THUNDERSTORM,
}

data class WeatherRisk(
    val kind: WeatherRiskKind,
    val severity: Int,
    val confidence: Double,
    val capturedAtEpochMs: Long,
    val validUntilEpochMs: Long,
)

object NavigationWeatherRiskPolicy {
    fun score(risk: WeatherRisk, nowEpochMs: Long): Double {
        if (risk.validUntilEpochMs < nowEpochMs) return 0.0
        if (risk.severity !in 0..5 || risk.confidence !in 0.0..1.0) return 0.0
        val base = when (risk.kind) {
            WeatherRiskKind.NONE -> 0.0
            WeatherRiskKind.HEAVY_RAIN -> 0.6
            WeatherRiskKind.ICE -> 1.0
            WeatherRiskKind.SNOW -> 0.9
            WeatherRiskKind.FOG -> 0.75
            WeatherRiskKind.STRONG_WIND -> 0.7
            WeatherRiskKind.THUNDERSTORM -> 0.95
        }
        return base * risk.severity / 5.0 * risk.confidence
    }

    fun shouldPrepareAlternative(risk: WeatherRisk, nowEpochMs: Long): Boolean =
        score(risk, nowEpochMs) >= 0.55
}
