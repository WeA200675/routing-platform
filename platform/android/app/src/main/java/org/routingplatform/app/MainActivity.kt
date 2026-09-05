package org.routingplatform.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.maplibre.android.MapLibre
import org.routingplatform.app.navigation.JniNavigationCoreBridge
import org.routingplatform.app.navigation.NavigationSessionState
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
                    JniNavigationCoreBridge()
                }

            var snapshot by
                remember {
                    mutableStateOf(
                        bridge.currentSnapshot()
                    )
                }

            var progressStep by
                remember {
                    mutableStateOf(0)
                }

            val progressUpdates =
                remember {
                    listOf(
                        0 to 0.50,
                        1 to 0.00,
                        1 to 0.50,
                        2 to 0.00,
                        2 to 0.50,
                        2 to 1.00,
                    )
                }

            DisposableEffect(
                snapshot.state
            ) {
                if (
                    snapshot.state ==
                        NavigationSessionState.Navigating
                ) {
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }

                onDispose {
                    window.clearFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }

            RoutingPlatformTheme {
                NavigationScreen(
                    snapshot =
                        snapshot,

                    onStartNavigation = {
                        progressStep =
                            0

                        snapshot =
                            bridge
                                .startNavigation()
                    },

                    onAdvanceProgress = {
                        if (
                            progressStep <
                                progressUpdates.size
                        ) {
                            val update =
                                progressUpdates[
                                    progressStep
                                ]

                            snapshot =
                                bridge
                                    .updateProgress(
                                        shapeSegmentIndex =
                                            update.first,

                                        segmentFraction =
                                            update.second,
                                    )

                            progressStep +=
                                1
                        }
                    },
                )
            }
        }
    }
}
