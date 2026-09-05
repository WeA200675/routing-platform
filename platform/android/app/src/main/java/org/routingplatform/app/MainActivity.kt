package org.routingplatform.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.MapLibre
import org.routingplatform.app.navigation.DemoNavigationCoreBridge
import org.routingplatform.app.ui.NavigationScreen
import org.routingplatform.app.ui.RoutingPlatformTheme

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState
        )

        MapLibre.getInstance(this)

        setContent {
            val bridge =
                remember {
                    DemoNavigationCoreBridge()
                }

            var snapshot by
                remember {
                    mutableStateOf(
                        bridge.currentSnapshot()
                    )
                }

            RoutingPlatformTheme {
                NavigationScreen(
                    snapshot =
                        snapshot,

                    onStartNavigation = {
                        snapshot =
                            bridge
                                .startNavigation()
                    },

                    onAdvanceDemo = {
                        snapshot =
                            bridge
                                .advanceDemo()
                    },
                )
            }
        }
    }
}
