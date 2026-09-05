package org.routingplatform.app.navigation

data class NativeNavigationSnapshot(
    val schemaVersion: Int,

    val sessionId: String,

    // Core enum ordinal:
    // Preview=0, Navigating=1, Arrived=2.
    val stateOrdinal: Int,

    val routeId: String,

    val routeDistanceM: Double,
    val routeDurationS: Double,

    // Flattened latitude/longitude pairs.
    val geometryLatLon: DoubleArray,

    val progressFraction: Double,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,

    val currentInstruction: String?,

    val distanceToCurrentManeuverEndM: Double,

    val arrived: Boolean,

    val rerouteRequested: Boolean,
    val routeRecomputed: Boolean,
    val routingEngineInvoked: Boolean,
    val candidateSelectionInvoked: Boolean,
    val costEngineInvoked: Boolean,
    val productionRouteMutationAllowed: Boolean,
) {

    fun toUiSnapshot():
        NavigationUiSnapshot {

        require(schemaVersion == 1) {
            "Unsupported native navigation schema."
        }

        require(
            geometryLatLon.size >= 4 &&
                geometryLatLon.size % 2 == 0
        ) {
            "Native geometry must contain lat/lon pairs."
        }

        val state =
            when (stateOrdinal) {
                0 ->
                    NavigationSessionState.Preview

                1 ->
                    NavigationSessionState.Navigating

                2 ->
                    NavigationSessionState.Arrived

                else ->
                    error(
                        "Unknown native navigation state."
                    )
            }

        val geometry =
            buildList {
                var index =
                    0

                while (
                    index <
                    geometryLatLon.size
                ) {
                    add(
                        RoutePoint(
                            latitude =
                                geometryLatLon[index],

                            longitude =
                                geometryLatLon[index + 1],
                        )
                    )

                    index +=
                        2
                }
            }

        val currentManeuver =
            currentInstruction
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    NavigationManeuver(
                        type =
                            ManeuverType.Continue,

                        instruction =
                            it,

                        distanceM =
                            distanceToCurrentManeuverEndM,

                        durationS =
                            0.0,
                    )
                }

        return NavigationUiSnapshot(
            schemaVersion =
                schemaVersion,

            sessionId =
                sessionId,

            state =
                state,

            routeId =
                routeId,

            routeDistanceM =
                routeDistanceM,

            routeDurationS =
                routeDurationS,

            geometry =
                geometry,

            progressFraction =
                progressFraction,

            remainingDistanceM =
                remainingDistanceM,

            remainingDurationS =
                remainingDurationS,

            currentManeuver =
                currentManeuver,

            nextManeuver =
                null,

            distanceToCurrentManeuverEndM =
                distanceToCurrentManeuverEndM,

            arrived =
                arrived,

            rerouteRequested =
                rerouteRequested,

            routeRecomputed =
                routeRecomputed,

            routingEngineInvoked =
                routingEngineInvoked,

            candidateSelectionInvoked =
                candidateSelectionInvoked,

            costEngineInvoked =
                costEngineInvoked,

            productionRouteMutationAllowed =
                productionRouteMutationAllowed,
        )
    }
}
