package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationWeatherRiskPolicyTest {
    @Test
    fun severeValidIceTriggersAlternativePreparation() {
        val risk = WeatherRisk(WeatherRiskKind.ICE, 5, 1.0, 0, 10_000)
        assertTrue(NavigationWeatherRiskPolicy.shouldPrepareAlternative(risk, 1_000))
    }

    @Test
    fun expiredRiskHasNoScore() {
        val risk = WeatherRisk(WeatherRiskKind.THUNDERSTORM, 5, 1.0, 0, 900)
        assertEquals(0.0, NavigationWeatherRiskPolicy.score(risk, 1_000), 0.0)
    }
}
