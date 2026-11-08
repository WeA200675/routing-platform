package org.routingplatform.app.navigation

data class RouteUpdateCandidate(
    val requestId: Long,
    val routeId: String,
    val score: RouteCandidateScore,
    val geometryPointCount: Int,
)

enum class RouteUpdateResult {
    ACCEPTED,
    REJECTED_NO_PENDING_REQUEST,
    REJECTED_UNSAFE,
    REJECTED_INCOMPLETE,
}

class NavigationRouteUpdateGate(
    private val minimumSafetyScore: Double = 0.70,
) {
    private var pendingRequestId: Long? = null
    private var activeRouteId: String? = null

    fun expect(requestId: Long) {
        pendingRequestId = requestId
    }

    fun activeRouteId(): String? = activeRouteId

    fun apply(candidate: RouteUpdateCandidate): RouteUpdateResult {
        if (pendingRequestId != candidate.requestId) {
            return RouteUpdateResult.REJECTED_NO_PENDING_REQUEST
        }
        if (candidate.score.safetyScore < minimumSafetyScore ||
            candidate.score.confidence < 0.50
        ) {
            return RouteUpdateResult.REJECTED_UNSAFE
        }
        if (candidate.routeId.isBlank() || candidate.geometryPointCount < 2) {
            return RouteUpdateResult.REJECTED_INCOMPLETE
        }
        activeRouteId = candidate.routeId
        pendingRequestId = null
        return RouteUpdateResult.ACCEPTED
    }

    fun clearPending() {
        pendingRequestId = null
    }
}
