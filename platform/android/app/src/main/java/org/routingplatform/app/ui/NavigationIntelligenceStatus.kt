package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationRuntimeIntelligenceState

@Composable
internal fun NavigationIntelligenceStatus(
    state: NavigationRuntimeIntelligenceState,
    modifier: Modifier = Modifier,
) {
    val telemetry = state.lastDecision
    val headline = when {
        telemetry == null -> "Navigation wird überwacht"
        telemetry.recalculationRequested -> "Neue Route wird vorbereitet"
        telemetry.liveAction.name == "PREPARE_ALTERNATIVE" -> "Alternative Route wird geprüft"
        telemetry.watchdogStatus.name == "HEALTHY" -> "Navigation stabil"
        else -> "Navigation prüft die Verbindung"
    }
    Column(modifier = modifier.padding(8.dp)) {
        Text(headline, style = MaterialTheme.typography.labelLarge)
        telemetry?.reason?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    }
}
