package org.routingplatform.app.ui

import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.profile.NavigationControlSide

data class NavigationControlPresentation(
    val side:
        NavigationControlSide,

    val label:
        String,

    val enabled:
        Boolean,

    val destructive:
        Boolean,
) {
    fun oppositeSide():
        NavigationControlSide =
        when (
            side
        ) {
            NavigationControlSide.Left ->
                NavigationControlSide.Right

            NavigationControlSide.Right ->
                NavigationControlSide.Left
        }

    companion object {
        fun create(
            state:
                NavigationSessionState,

            side:
                NavigationControlSide,

            navigationStartEnabled:
                Boolean,
        ): NavigationControlPresentation =
            when (
                state
            ) {
                NavigationSessionState.Preview ->
                    NavigationControlPresentation(
                        side =
                            side,

                        label =
                            if (
                                navigationStartEnabled
                            ) {
                                "Navigation starten"
                            } else {
                                "Produktionsroute nicht verfügbar"
                            },

                        enabled =
                            navigationStartEnabled,

                        destructive =
                            false,
                    )

                NavigationSessionState.Navigating ->
                    NavigationControlPresentation(
                        side =
                            side,

                        label =
                            "Navigation stoppen",

                        enabled =
                            true,

                        destructive =
                            true,
                    )

                NavigationSessionState.Arrived ->
                    NavigationControlPresentation(
                        side =
                            side,

                        label =
                            "Ziel erreicht",

                        enabled =
                            false,

                        destructive =
                            false,
                    )
            }
    }
}