package org.routingplatform.app.navigation

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NavigationLiveProviderConfigTest {
    @Test
    fun rejectsNonHttpsProviderEndpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            NavigationLiveProviderConfig(trafficEndpoint = URI("http://example.test/feed"))
        }
    }

    @Test
    fun acceptsOptionalProviderEndpointsAndReportsAvailability() {
        val config = NavigationLiveProviderConfig(
            weatherEndpoint = URI("https://weather.example.test/feed"),
            cacheMaxAgeMs = 60_000,
        )
        assertEquals(true, config.hasAnyProvider())
        assertEquals(60_000, config.cacheMaxAgeMs)
    }
}
