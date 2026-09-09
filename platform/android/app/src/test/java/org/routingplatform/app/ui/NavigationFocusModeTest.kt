package org.routingplatform.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFocusModeTest {
    @Test
    fun inactiveNavigationNeverFocuses() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        false,
                    navigationStartedElapsedRealtimeMs =
                        0L,
                    lastUserActivityElapsedRealtimeMs =
                        null,
                    interactionActive =
                        false,
                    nowElapsedRealtimeMs =
                        1_000_000L,
                )

        assertTrue(
            presentation.controlsVisible
        )

        assertFalse(
            presentation.focused
        )
    }

    @Test
    fun navigationStartsExpandedForThirtySeconds() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        true,
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        null,
                    interactionActive =
                        false,
                    nowElapsedRealtimeMs =
                        30_999L,
                )

        assertTrue(
            presentation.controlsVisible
        )

        assertFalse(
            presentation.focused
        )
    }

    @Test
    fun navigationFocusesAtInitialDeadline() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        true,
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        null,
                    interactionActive =
                        false,
                    nowElapsedRealtimeMs =
                        31_000L,
                )

        assertFalse(
            presentation.controlsVisible
        )

        assertTrue(
            presentation.focused
        )
    }

    @Test
    fun activeMapInteractionPreventsAutoHide() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        true,
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        null,
                    interactionActive =
                        true,
                    nowElapsedRealtimeMs =
                        100_000L,
                )

        assertTrue(
            presentation.controlsVisible
        )

        assertTrue(
            presentation.interactionActive
        )
    }

    @Test
    fun tapAfterFocusRevealsControlsTemporarily() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        true,
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        40_000L,
                    interactionActive =
                        false,
                    nowElapsedRealtimeMs =
                        47_999L,
                )

        assertTrue(
            presentation.controlsVisible
        )

        assertFalse(
            presentation.focused
        )
    }

    @Test
    fun controlsHideAgainAfterRevealTimeout() {
        val presentation =
            NavigationFocusModePolicy
                .presentation(
                    navigationActive =
                        true,
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        40_000L,
                    interactionActive =
                        false,
                    nowElapsedRealtimeMs =
                        48_000L,
                )

        assertFalse(
            presentation.controlsVisible
        )

        assertTrue(
            presentation.focused
        )
    }

    @Test
    fun earlyActivityNeverShortensInitialExpandedWindow() {
        val deadline =
            NavigationFocusModePolicy
                .collapseDeadlineElapsedRealtimeMs(
                    navigationStartedElapsedRealtimeMs =
                        10_000L,
                    lastUserActivityElapsedRealtimeMs =
                        12_000L,
                )

        assertTrue(
            deadline ==
                40_000L
        )
    }

    @Test
    fun lateInteractionEndGetsFreshRevealGracePeriod() {
        val deadline =
            NavigationFocusModePolicy
                .collapseDeadlineElapsedRealtimeMs(
                    navigationStartedElapsedRealtimeMs =
                        1_000L,
                    lastUserActivityElapsedRealtimeMs =
                        50_000L,
                )

        assertTrue(
            deadline ==
                58_000L
        )
    }
}