package org.routingplatform.app.navigation

import android.content.Intent

object NavigationRouteIntentRequest {
    const val ORIGIN_LATITUDE =
        "org.routingplatform.navigation.originLatitude"

    const val ORIGIN_LONGITUDE =
        "org.routingplatform.navigation.originLongitude"

    const val DESTINATION_LATITUDE =
        "org.routingplatform.navigation.destinationLatitude"

    const val DESTINATION_LONGITUDE =
        "org.routingplatform.navigation.destinationLongitude"

    const val VIA_POINTS =
        "org.routingplatform.navigation.viaPoints"

    const val FAMILY =
        "org.routingplatform.navigation.family"

    fun fromIntent(
        intent: Intent?,
    ): NavigationRouteRequest {

        val origin =
            RoutePoint(
                latitude =
                    intent.doubleExtraOrDefault(
                        ORIGIN_LATITUDE,
                        DEFAULT_ORIGIN_LATITUDE,
                    ),

                longitude =
                    intent.doubleExtraOrDefault(
                        ORIGIN_LONGITUDE,
                        DEFAULT_ORIGIN_LONGITUDE,
                    ),
            )

        val destination =
            RoutePoint(
                latitude =
                    intent.doubleExtraOrDefault(
                        DESTINATION_LATITUDE,
                        DEFAULT_DESTINATION_LATITUDE,
                    ),

                longitude =
                    intent.doubleExtraOrDefault(
                        DESTINATION_LONGITUDE,
                        DEFAULT_DESTINATION_LONGITUDE,
                    ),
            )

        val viaPoints =
            parseViaPoints(
                intent
                    ?.extras
                    ?.get(
                        VIA_POINTS
                    )
                    ?.toString()
            )

        val family =
            parseFamily(
                intent
                    ?.extras
                    ?.get(
                        FAMILY
                    )
                    ?.toString()
            )

        return NavigationRouteRequest(
            origin =
                origin,

            destination =
                destination,

            viaPoints =
                viaPoints,

            family =
                family,
        )
    }
}

private fun Intent?.doubleExtraOrDefault(
    key: String,
    fallback: Double,
): Double {

    val raw =
        this
            ?.extras
            ?.get(
                key
            )
            ?: return fallback

    return when (
        raw
    ) {
        is Number ->
            raw.toDouble()

        else ->
            raw
                .toString()
                .toDoubleOrNull()
                ?: fallback
    }
}

private fun parseViaPoints(
    raw: String?,
): List<RoutePoint> {

    if (
        raw.isNullOrBlank()
    ) {
        return emptyList()
    }

    return raw
        .split(
            ';'
        )
        .filter {
            it.isNotBlank()
        }
        .map {
                item ->

            val parts =
                item.split(
                    ','
                )

            require(
                parts.size ==
                    2
            ) {
                "Invalid navigation via point: $item"
            }

            RoutePoint(
                latitude =
                    parts[
                        0
                    ]
                        .trim()
                        .toDouble(),

                longitude =
                    parts[
                        1
                    ]
                        .trim()
                        .toDouble(),
            )
        }
}

private fun parseFamily(
    raw: String?,
): NavigationRouteFamily =
    when (
        raw
            ?.trim()
            ?.lowercase()
    ) {
        null,
        "",
        "profile_optimal" ->
            NavigationRouteFamily.ProfileOptimal

        "fastest" ->
            NavigationRouteFamily.Fastest

        "shortest" ->
            NavigationRouteFamily.Shortest

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
                "Unsupported navigation route family: $raw"
            )
    }

/*
 * Defaults are development startup coordinates only.
 *
 * They are deliberately independent of navigation_route.json.
 * Intent extras may replace every value at runtime.
 */
private const val DEFAULT_ORIGIN_LATITUDE =
    47.1410

private const val DEFAULT_ORIGIN_LONGITUDE =
    9.5209

private const val DEFAULT_DESTINATION_LATITUDE =
    47.1660

private const val DEFAULT_DESTINATION_LONGITUDE =
    9.5100