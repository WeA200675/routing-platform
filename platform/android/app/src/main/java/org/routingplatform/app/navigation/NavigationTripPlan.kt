package org.routingplatform.app.navigation

data class NavigationTripStop(
    val point:
        RoutePoint,

    val label:
        String? =
        null,
) {
    init {
        require(
            point.latitude.isFinite() &&
                point.latitude in
                    -90.0..90.0 &&
                point.longitude.isFinite() &&
                point.longitude in
                    -180.0..180.0
        ) {
            "Trip stop coordinates are invalid."
        }

        label
            ?.let {
                val trimmed =
                    it.trim()

                require(
                    trimmed.isNotEmpty() &&
                        trimmed.length <=
                            240
                ) {
                    "Trip stop label is invalid."
                }

                require(
                    trimmed.none {
                            character ->

                        character.code <
                            0x20 ||
                            character.code ==
                            0x7f
                    }
                ) {
                    "Trip stop label contains control characters."
                }
            }
    }
}

data class NavigationTripPlan(
    val destination:
        NavigationTripStop,

    val viaPoints:
        List<NavigationTripStop> =
        emptyList(),
) {
    init {
        require(
            viaPoints.size <=
                MAX_NAVIGATION_TRIP_VIA_POINTS
        ) {
            "Too many trip via points."
        }
    }

    fun withDestination(
        destination:
            NavigationTripStop,
    ): NavigationTripPlan =
        copy(
            destination =
                destination
        )

    fun appendVia(
        via:
            NavigationTripStop,
    ): NavigationTripPlan {
        require(
            viaPoints.size <
                MAX_NAVIGATION_TRIP_VIA_POINTS
        ) {
            "Too many trip via points."
        }

        return copy(
            viaPoints =
                viaPoints +
                    via
        )
    }

    fun removeVia(
        index:
            Int,
    ): NavigationTripPlan {
        require(
            index in
                viaPoints.indices
        ) {
            "Via-point index is out of range."
        }

        return copy(
            viaPoints =
                viaPoints.filterIndexed {
                        currentIndex,
                        _ ->

                    currentIndex !=
                        index
                }
        )
    }

    fun moveViaUp(
        index:
            Int,
    ): NavigationTripPlan =
        moveVia(
            from =
                index,

            to =
                index -
                    1,
        )

    fun moveViaDown(
        index:
            Int,
    ): NavigationTripPlan =
        moveVia(
            from =
                index,

            to =
                index +
                    1,
        )

    fun toRouteRequest(
        origin:
            RoutePoint,

        family:
            NavigationRouteFamily,
    ): NavigationRouteRequest =
        NavigationRouteRequest(
            origin =
                origin,

            destination =
                destination.point,

            viaPoints =
                viaPoints.map {
                    it.point
                },

            family =
                family,
        )

    private fun moveVia(
        from:
            Int,

        to:
            Int,
    ): NavigationTripPlan {
        require(
            from in
                viaPoints.indices
        ) {
            "Via-point source index is out of range."
        }

        require(
            to in
                viaPoints.indices
        ) {
            "Via-point target index is out of range."
        }

        if (
            from ==
                to
        ) {
            return this
        }

        val reordered =
            viaPoints
                .toMutableList()

        val value =
            reordered.removeAt(
                from
            )

        reordered.add(
            to,
            value,
        )

        return copy(
            viaPoints =
                reordered
                    .toList()
        )
    }

    companion object {
        fun fromRequest(
            request:
                NavigationRouteRequest,
        ): NavigationTripPlan =
            NavigationTripPlan(
                destination =
                    NavigationTripStop(
                        point =
                            request.destination
                    ),

                viaPoints =
                    request.viaPoints
                        .map {
                            NavigationTripStop(
                                point =
                                    it
                            )
                        },
            )
    }
}

const val MAX_NAVIGATION_TRIP_VIA_POINTS =
    16
