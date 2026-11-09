package org.routingplatform.app.navigation

/**
 * Applies current live events to route candidates without allowing stale or
 * low-confidence feeds to make a route appear safer than it is.
 */
object NavigationLiveRouteCandidateScorer {
    fun score(
        route: NavigationRouteContract,
        events: List<NavigationLiveEvent>,
        nowEpochMs: Long,
    ): RouteCandidateScore {
        val relevant = NavigationLiveEventPrioritizer
            .prioritize(events, nowEpochMs)
            .filter { decision -> route.geometry.any { point ->
                distanceMeters(point, RoutePoint(decision.event.latitude, decision.event.longitude)) <= 1_500.0
            } }
        var safety = 1.0
        var delay = 0.0
        relevant.forEach { decision ->
            val event = decision.event
            val impact = (event.severity / 5.0) * event.confidence
            when (event.kind) {
                LiveEventKind.HAZARD,
                LiveEventKind.ROAD_CLOSURE,
                LiveEventKind.ACCIDENT -> {
                    safety -= if (event.kind == LiveEventKind.ROAD_CLOSURE) impact * 0.85 else impact * 0.35
                    delay += event.severity * 90.0
                }
                LiveEventKind.TRAFFIC_JAM,
                LiveEventKind.WEATHER -> {
                    safety -= impact * 0.15
                    delay += event.severity * 60.0
                }
                LiveEventKind.POI_UPDATE -> Unit
            }
        }
        return RouteCandidateScore(
            routeId = route.routeId,
            safetyScore = safety.coerceIn(0.0, 1.0),
            timeSeconds = route.durationS + delay,
            costScore = route.distanceM,
            confidence = if (relevant.isEmpty()) 0.85 else 0.95,
        )
    }

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val lat = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val x = dLon * kotlin.math.cos(lat)
        val y = dLat
        return kotlin.math.sqrt(x * x + y * y) * 6_371_000.0
    }
}
