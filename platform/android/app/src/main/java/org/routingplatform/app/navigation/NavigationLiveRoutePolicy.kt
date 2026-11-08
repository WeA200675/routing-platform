package org.routingplatform.app.navigation

enum class LiveRouteAction {
    KEEP_ROUTE,
    PREPARE_ALTERNATIVE,
    REQUEST_IMMEDIATE_RECALCULATION,
}

object NavigationLiveRoutePolicy {
    fun decide(
        events: List<LiveEventDecision>,
        nearestEventDistanceMeters: Double?,
    ): LiveRouteAction {
        val distance = nearestEventDistanceMeters ?: Double.POSITIVE_INFINITY
        val critical = events.any {
            it.event.kind == LiveEventKind.HAZARD ||
                it.event.kind == LiveEventKind.ROAD_CLOSURE ||
                it.event.kind == LiveEventKind.ACCIDENT
        }
        val severe = events.any { it.event.severity >= 4 && it.event.confidence >= 0.7 }
        val near = distance <= 5_000.0

        return when {
            critical && severe && near -> LiveRouteAction.REQUEST_IMMEDIATE_RECALCULATION
            critical || severe -> LiveRouteAction.PREPARE_ALTERNATIVE
            events.any { it.event.kind == LiveEventKind.TRAFFIC_JAM } ->
                LiveRouteAction.PREPARE_ALTERNATIVE
            else -> LiveRouteAction.KEEP_ROUTE
        }
    }
}
