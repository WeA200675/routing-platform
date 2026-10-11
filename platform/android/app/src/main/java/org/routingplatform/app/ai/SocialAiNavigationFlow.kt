package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteAcquisitionTelemetry
import org.routingplatform.app.navigation.NavigationRouteLifecycleController
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint

sealed interface SocialAiNavigationFlowResult {
    data class Submitted(val viaPointCount: Int) : SocialAiNavigationFlowResult
    data class ClarificationRequired(val reason: String) : SocialAiNavigationFlowResult
}

class SocialAiNavigationFlow(
    private val requestBridge: SocialAiNavigationRequestBridge,
    private val routeController: NavigationRouteLifecycleController,
) {
    fun submit(
        userText: String,
        origin: RoutePoint,
        snapshotProvider: () -> NavigationUiSnapshot,
        onSnapshot: (NavigationUiSnapshot) -> Unit,
        onTelemetry: (NavigationRouteAcquisitionTelemetry) -> Unit,
    ): SocialAiNavigationFlowResult =
        when (val built = requestBridge.buildRequest(origin, userText)) {
            is SocialAiNavigationRequestResult.ClarificationRequired ->
                SocialAiNavigationFlowResult.ClarificationRequired(built.reason)
            is SocialAiNavigationRequestResult.Ready -> {
                routeController.loadInitial(
                    request = built.request,
                    snapshotProvider = snapshotProvider,
                    onSnapshot = onSnapshot,
                    onTelemetry = onTelemetry,
                )
                SocialAiNavigationFlowResult.Submitted(built.request.viaPoints.size)
            }
        }
}
