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
                snapshot = degradedSnapshot(error),
                productionRouteReady = false,
                errorMessage =
                    "Produktionsroute nicht verfügbar: " +
                        (error.message ?: "unbekannter Ladefehler"),
            )
        }
    }

    private fun degradedSnapshot(error: Throwable): NavigationUiSnapshot =
        NavigationUiSnapshot(
            sessionId = "bootstrap-degraded",
            state = NavigationSessionState.Preview,
            routeId = "bootstrap-degraded-route",
            routeDistanceM = 1.0,
            routeDurationS = 1.0,
            geometry = listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.00001)),
            progressFraction = 0.0,
            remainingDistanceM = 1.0,
            remainingDurationS = 1.0,
            currentManeuver = null,
            nextManeuver = null,
            distanceToCurrentManeuverEndM = 0.0,
            arrived = false,
            routeDiagnostics = listOf(
                NavigationRouteDiagnostic(
                    code = "ROUTE_BOOTSTRAP_FAILED",
                    message = error.message ?: "unbekannter Ladefehler",
                )
            ),
        )

}