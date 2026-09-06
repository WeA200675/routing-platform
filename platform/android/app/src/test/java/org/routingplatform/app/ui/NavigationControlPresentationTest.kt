package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.profile.NavigationControlSide

class NavigationControlPresentationTest {

    @Test
    fun previewUsesStartControl() {
        val result =
            NavigationControlPresentation
                .create(
                    state =
                        NavigationSessionState.Preview,

                    side =
                        NavigationControlSide.Right,

                    navigationStartEnabled =
                        true,
                )

        assertEquals(
            "Navigation starten",
            result.label,
        )

        assertTrue(
            result.enabled
        )

        assertFalse(
            result.destructive
        )
    }

    @Test
    fun navigatingUsesDestructiveStopControl() {
        val result =
            NavigationControlPresentation
                .create(
                    state =
                        NavigationSessionState.Navigating,

                    side =
                        NavigationControlSide.Left,

                    navigationStartEnabled =
                        true,
                )

        assertEquals(
            "Navigation stoppen",
            result.label,
        )

        assertTrue(
            result.enabled
        )

        assertTrue(
            result.destructive
        )

        assertEquals(
            NavigationControlSide.Right,
            result.oppositeSide(),
        )
    }
}