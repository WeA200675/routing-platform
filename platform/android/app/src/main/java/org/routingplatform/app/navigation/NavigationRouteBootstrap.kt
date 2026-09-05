package org.routingplatform.app.navigation

import android.content.Context

data class NavigationRouteBootstrapResult(
    val snapshot:
        NavigationUiSnapshot,

    val productionRouteReady:
        Boolean,

    val errorMessage:
        String?,
)

object NavigationRouteBootstrap {
    const val DEFAULT_ASSET_NAME =
        "navigation_route.json"

    fun load(
        context: Context,
        bridge: JniNavigationCoreBridge,
        assetName:
            String =
            DEFAULT_ASSET_NAME,
    ): NavigationRouteBootstrapResult {

        return runCatching {
            val json =
                context.assets
                    .open(
                        assetName
                    )
                    .bufferedReader(
                        Charsets.UTF_8
                    )
                    .use {
                        it.readText()
                    }

            val route =
                AndroidNavigationRouteContractJson
                    .parse(
                        json
                    )

            val snapshot =
                bridge.installRoute(
                    route
                )

            check(
                snapshot.routeId ==
                    route.routeId
            ) {
                "Installed native route id does not match route contract."
            }

            NavigationRouteBootstrapResult(
                snapshot =
                    snapshot,

                productionRouteReady =
                    true,

                errorMessage =
                    null,
            )
        }.getOrElse {
                error ->

            NavigationRouteBootstrapResult(
                snapshot =
                    bridge.currentSnapshot(),

                productionRouteReady =
                    false,

                errorMessage =
                    "Produktionsroute nicht verfügbar: " +
                        (
                            error.message
                                ?: "unbekannter Ladefehler"
                        ),
            )
        }
    }
}