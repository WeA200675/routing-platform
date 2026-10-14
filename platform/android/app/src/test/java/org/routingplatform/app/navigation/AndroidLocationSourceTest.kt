package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidLocationSourceTest {
    @Test
    fun prefersFusedProviderWhenAvailableOnAndroidSOrLater() {
        assertEquals(
            listOf("fused"),
            selectNavigationProviders(
                apiLevel = 31,
                enabledProviders = setOf("fused", "gps", "network"),
            ),
        )
    }

    @Test
    fun fallsBackToGpsAndNetworkWhenFusedProviderIsUnavailable() {
        assertEquals(
            listOf("gps", "network"),
            selectNavigationProviders(
                apiLevel = 35,
                enabledProviders = setOf("gps", "network"),
            ),
        )
    }

    @Test
    fun olderAndroidUsesAvailableLegacyProviders() {
        assertEquals(
            listOf("gps"),
            selectNavigationProviders(
                apiLevel = 30,
                enabledProviders = setOf("fused", "gps"),
            ),
        )
    }

    @Test
    fun locationFixMustBeFreshAndNotFromTheFuture() {
        assertEquals(
            true,
            isNavigationLocationFixFresh(
                fixElapsedRealtimeNanos = 9_000_000_000L,
                nowElapsedRealtimeNanos = 10_000_000_000L,
            ),
        )
        assertEquals(
            false,
            isNavigationLocationFixFresh(
                fixElapsedRealtimeNanos = 1L,
                nowElapsedRealtimeNanos = 20_000_000_000L,
            ),
        )
        assertEquals(
            false,
            isNavigationLocationFixFresh(
                fixElapsedRealtimeNanos = 12_000_000_001L,
                nowElapsedRealtimeNanos = 10_000_000_000L,
            ),
        )
    }

    @Test
    fun noEnabledProviderProducesAnEmptySelection() {
        assertEquals(
            emptyList<String>(),
            selectNavigationProviders(
                apiLevel = 35,
                enabledProviders = emptySet(),
            ),
        )
    }
}
