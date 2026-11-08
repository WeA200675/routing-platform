package org.routingplatform.app.ui

import org.routingplatform.app.navigation.NavigationRuntimeCycleResult

enum class NavigationIntelligenceUiState {
    STABLE,
    LIVE_DATA_DEGRADED,
    ALTERNATIVE_PREPARING,
    RECALCULATING,
    OFFLINE,
}

data class NavigationIntelligencePresentation(
    val state: NavigationIntelligenceUiState,
    val title: String,
    val detail: String?,
)

object NavigationIntelligencePresentationMapper {
    fun map(result: NavigationRuntimeCycleResult): NavigationIntelligencePresentation {
        val telemetry = result.decision.lastDecision
        val state = when {
            result.nextRefreshInMs == Long.MAX_VALUE ->
                NavigationIntelligenceUiState.OFFLINE
            telemetry?.recalculationRequested == true ->
                NavigationIntelligenceUiState.RECALCULATING
            telemetry?.liveAction?.name == "PREPARE_ALTERNATIVE" ->
                NavigationIntelligenceUiState.ALTERNATIVE_PREPARING
            result.liveContext.eventCount == 0 ->
                NavigationIntelligenceUiState.LIVE_DATA_DEGRADED
            else ->
                NavigationIntelligenceUiState.STABLE
        }
        val title = when (state) {
            NavigationIntelligenceUiState.STABLE -> "Navigation stabil"
            NavigationIntelligenceUiState.LIVE_DATA_DEGRADED -> "Live-Daten eingeschränkt"
            NavigationIntelligenceUiState.ALTERNATIVE_PREPARING -> "Alternative Route wird geprüft"
            NavigationIntelligenceUiState.RECALCULATING -> "Neue Route wird berechnet"
            NavigationIntelligenceUiState.OFFLINE -> "Offline-Fallback aktiv"
        }
        return NavigationIntelligencePresentation(state, title, telemetry?.reason)
    }
}
