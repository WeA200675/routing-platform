package org.routingplatform.app.navigation

import org.json.JSONObject

object AndroidNavigationRouteContractJson {
    fun parse(
        json: String,
    ): NavigationRouteContract {

        val root =
            JSONObject(
                json
            )

        val schemaVersion =
            root.getInt(
                "schemaVersion"
            )

        val geometryJson =
            root.getJSONArray(
                "geometry"
            )

        val geometry =
            buildList {
                for (
                    index in
                        0 until geometryJson.length()
                ) {
                    val point =
                        geometryJson
                            .getJSONArray(
                                index
                            )

                    add(
                        RoutePoint(
                            latitude =
                                point.getDouble(
                                    0
                                ),

                            longitude =
                                point.getDouble(
                                    1
                                ),
                        )
                    )
                }
            }

        val maneuversJson =
            root.getJSONArray(
                "maneuvers"
            )

        val maneuvers =
            buildList {
                for (
                    index in
                        0 until maneuversJson.length()
                ) {
                    val source =
                        maneuversJson
                            .getJSONObject(
                                index
                            )

                    val streetNamesJson =
                        source.getJSONArray(
                            "streetNames"
                        )

                    val streetNames =
                        buildList {
                            for (
                                streetIndex in
                                    0 until streetNamesJson.length()
                            ) {
                                add(
                                    streetNamesJson
                                        .getString(
                                            streetIndex
                                        )
                                )
                            }
                        }

                    add(
                        NavigationRouteContractManeuver(
                            type =
                                parseManeuverType(
                                    source.getString(
                                        "type"
                                    )
                                ),

                            instruction =
                                source.getString(
                                    "instruction"
                                ),

                            streetNames =
                                streetNames,

                            distanceM =
                                source.getDouble(
                                    "distanceM"
                                ),

                            durationS =
                                source.getDouble(
                                    "durationS"
                                ),

                            beginShapeIndex =
                                source.getInt(
                                    "beginShapeIndex"
                                ),

                            endShapeIndex =
                                source.getInt(
                                    "endShapeIndex"
                                ),

                            bearingBeforeDeg =
                                source.optionalInt(
                                    "bearingBeforeDeg"
                                ),

                            bearingAfterDeg =
                                source.optionalInt(
                                    "bearingAfterDeg"
                                ),

                            engineType =
                                source.optionalInt(
                                    "engineType"
                                ),
                        )
                    )
                }
            }

        val diagnosticsJson =
            root.optJSONArray(
                "diagnostics"
            )

        val diagnostics =
            buildList {
                if (
                    diagnosticsJson !=
                        null
                ) {
                    for (
                        index in
                            0 until diagnosticsJson.length()
                    ) {
                        val source =
                            diagnosticsJson
                                .getJSONObject(
                                    index
                                )

                        add(
                            NavigationRouteDiagnostic(
                                code =
                                    source.getString(
                                        "code"
                                    ),

                                message =
                                    source.getString(
                                        "message"
                                    ),
                            )
                        )
                    }
                }
            }

        return NavigationRouteContract(
            schemaVersion =
                schemaVersion,

            routeId =
                root.getString(
                    "routeId"
                ),

            family =
                parseFamily(
                    root.getString(
                        "family"
                    )
                ),

            distanceM =
                root.getDouble(
                    "distanceM"
                ),

            durationS =
                root.getDouble(
                    "durationS"
                ),

            geometry =
                geometry,

            maneuvers =
                maneuvers,

            engineName =
                root.getString(
                    "engineName"
                ),

            engineVersion =
                root.getString(
                    "engineVersion"
                ),

            segmentDataStatus =
                parseSegmentDataStatus(
                    root.getString(
                        "segmentDataStatus"
                    )
                ),

            diagnostics =
                diagnostics,
        )
    }
}

private fun JSONObject.optionalInt(
    key: String,
): Int? =
    if (
        !has(
            key
        ) ||
        isNull(
            key
        )
    ) {
        null
    } else {
        getInt(
            key
        )
    }

private fun parseFamily(
    value: String,
): NavigationRouteFamily =
    when (
        value
    ) {
        "fastest" ->
            NavigationRouteFamily.Fastest

        "shortest" ->
            NavigationRouteFamily.Shortest

        "profile_optimal" ->
            NavigationRouteFamily.ProfileOptimal

        "major_roads" ->
            NavigationRouteFamily.MajorRoads

        "comfort" ->
            NavigationRouteFamily.Comfort

        "low_urban" ->
            NavigationRouteFamily.LowUrban

        "low_curvature" ->
            NavigationRouteFamily.LowCurvature

        "low_gradient" ->
            NavigationRouteFamily.LowGradient

        "low_traffic" ->
            NavigationRouteFamily.LowTraffic

        "energy" ->
            NavigationRouteFamily.Energy

        "scenic" ->
            NavigationRouteFamily.Scenic

        "stable" ->
            NavigationRouteFamily.Stable

        else ->
            error(
                "Unknown navigation route family: $value"
            )
    }

private fun parseSegmentDataStatus(
    value: String,
): NavigationRouteSegmentDataStatus =
    when (
        value
    ) {
        "unspecified" ->
            NavigationRouteSegmentDataStatus.Unspecified

        "complete" ->
            NavigationRouteSegmentDataStatus.Complete

        "unavailable" ->
            NavigationRouteSegmentDataStatus.Unavailable

        else ->
            error(
                "Unknown route segment data status: $value"
            )
    }

private fun parseManeuverType(
    value: String,
): ManeuverType =
    when (
        value
    ) {
        "unknown" ->
            ManeuverType.Unknown

        "start" ->
            ManeuverType.Start

        "continue" ->
            ManeuverType.Continue

        "turn_left" ->
            ManeuverType.TurnLeft

        "turn_right" ->
            ManeuverType.TurnRight

        "u_turn" ->
            ManeuverType.UTurn

        "merge" ->
            ManeuverType.Merge

        "exit" ->
            ManeuverType.Exit

        "roundabout_enter" ->
            ManeuverType.RoundaboutEnter

        "roundabout_exit" ->
            ManeuverType.RoundaboutExit

        "arrive" ->
            ManeuverType.Arrive

        else ->
            error(
                "Unknown maneuver type: $value"
            )
    }