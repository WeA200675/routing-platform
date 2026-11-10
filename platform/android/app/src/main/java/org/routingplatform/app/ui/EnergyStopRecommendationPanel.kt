package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.vehicle.EnergyStopCandidate
import org.routingplatform.app.vehicle.EnergyStopKind

@Composable
internal fun EnergyStopRecommendationPanel(
    candidate: EnergyStopCandidate?,
    onUseAsViaPoint: (EnergyStopCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (candidate == null) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Empfohlener Energiestopp", style = MaterialTheme.typography.titleMedium)
        Text(candidate.poi.title)
        Text(
            text = (if (candidate.kind == EnergyStopKind.FUEL) "Tankstelle" else "Ladestation") +
                " · " + distance(candidate.poi.distanceMeters),
        )
        Text(
            text = candidate.estimatedCost?.let { "Preis: %.3f pro Einheit".format(it) }
                ?: "Preis: keine Live-Daten verfügbar",
        )
        Text(
            text = when (candidate.available) {
                true -> "Verfügbarkeit: verfügbar"
                false -> "Verfügbarkeit: nicht verfügbar"
                null -> "Verfügbarkeit: unbekannt"
            },
        )
        Button(onClick = { onUseAsViaPoint(candidate) }) {
            Text("Als Zwischenziel übernehmen")
        }
    }
}

private fun distance(meters: Double): String =
    if (meters < 1_000.0) meters.toInt().toString() + " m"
    else "%.1f km".format(meters / 1_000.0)
