package org.routingplatform.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import kotlin.math.roundToInt

@Composable
internal fun ActiveRouteStopSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onClick, modifier = modifier) {
        Text("＋ Zwischenziel")
    }
}

internal data class ActiveRoutePoiRouteEstimate(
    val addedDurationSeconds: Double,
    val addedDistanceMeters: Double,
)

@Composable
internal fun ActiveRouteStopSearchDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<DestinationSearchResult>,
    routeGeometry: List<RoutePoint>,
    routeEstimates: Map<String, ActiveRoutePoiRouteEstimate>,
    routeEvaluationInProgress: Boolean,
    routeEvaluationComplete: Boolean,
    routeEvaluationUnavailable: Boolean,
    busy: Boolean,
    message: String?,
    maximumViaPointsReached: Boolean,
    maximumDetourMinutes: Int,
    onMaximumDetourMinutesChange: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onAddStop: (DestinationSearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedResult by remember { mutableStateOf<DestinationSearchResult?>(null) }
    val rankedResults = remember(results, routeGeometry, routeEstimates, routeEvaluationComplete) {
        val ordered = if (routeEstimates.isNotEmpty()) {
            results.sortedWith(
                compareBy<DestinationSearchResult> {
                    routeEstimates[it.id]?.addedDurationSeconds ?: Double.POSITIVE_INFINITY
                }.thenBy {
                    routeEstimates[it.id]?.addedDistanceMeters ?: Double.POSITIVE_INFINITY
                }
            )
        } else {
            results.sortedBy { distanceFromRouteMeters(it.point, routeGeometry) }
        }
        ordered.map { result ->
            result to routeEstimates[result.id]
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text("Was brauchst du unterwegs?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nenne einen Ort oder wähle eine Kategorie. Ich vergleiche erreichbare Treffer mit der Route und deinem Umweglimit. Du bestätigst den Stopp; bis zur erfolgreichen Berechnung bleibt die Navigation unverändert.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        selectedResult = null
                        onQueryChange(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Wunsch, Firmenname oder Adresse") },
                    singleLine = true,
                    enabled = !busy,
                )
                Spacer(Modifier.height(6.dp))
                Text("Schnellsuche", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                val categories = listOf(
                    "Restaurant", "Tankstelle", "E-Ladestation", "Parkplatz",
                    "WC", "Apotheke", "Supermarkt", "Hotel",
                )
                categories.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { category ->
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    selectedResult = null
                                    onQueryChange(category)
                                    onSearch(category)
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(category)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Wie viel zusätzliche Fahrzeit ist okay?", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 20, 30).forEach { minutes ->
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { onMaximumDetourMinutesChange(minutes) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                if (maximumDetourMinutes == minutes) "${minutes}m ✓"
                                else "${minutes}m",
                            )
                        }
                    }
                }
                Text(
                    "Der neue Fahrweg wird erst nach Auswahl exakt berechnet. Liegt der Mehrweg über deinem Limit, bleibt die bisherige Route aktiv.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        selectedResult = null
                        onSearch(query)
                    },
                    enabled = !busy && query.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Suche läuft …" else "Orte suchen")
                }
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                }
                if (rankedResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (routeEstimates.isNotEmpty()) {
                            "Treffer – nach tatsächlich berechneter Zusatzfahrzeit sortiert"
                        } else {
                            "Treffer – vorläufig nach Luftliniennähe zur Route sortiert"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    rankedResults.forEach { (result, _) ->
                        val selected = selectedResult?.id == result.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clickable(enabled = !busy) { selectedResult = result },
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(result.primaryLabel, fontWeight = FontWeight.SemiBold)
                                result.secondaryLabel?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                                val estimate = routeEstimates[result.id]
                                when {
                                    estimate != null -> {
                                        val extraMinutes = kotlin.math.ceil(
                                            estimate.addedDurationSeconds / 60.0
                                        ).toInt()
                                        val overBudget =
                                            estimate.addedDurationSeconds > maximumDetourMinutes * 60.0
                                        Text(
                                            "Fahr-Umweg: +${extraMinutes} min · +${(estimate.addedDistanceMeters / 1000.0 * 10.0).roundToInt() / 10.0} km" +
                                                if (overBudget) " · über deinem Limit" else " · innerhalb deines Limits",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    routeEvaluationInProgress -> Text(
                                        "Echte Fahrzeit wird berechnet …",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    routeEvaluationComplete && !routeEvaluationUnavailable -> Text(
                                        "Keine passende Fahrroute berechnet",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    routeEvaluationUnavailable -> Text(
                                        "Vorprüfung nicht verfügbar – beim Einfügen wird die Route erneut geprüft",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    else -> Text(
                                        "Umweg noch nicht geprüft",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                } else if (!busy && message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Noch keine Treffer. Suche mit einem genaueren Namen oder einer Adresse.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { selectedResult?.let(onAddStop) },
                    enabled = !busy &&
                        !maximumViaPointsReached &&
                        selectedResult != null &&
                        !routeEvaluationInProgress &&
                        (
                            routeEvaluationUnavailable ||
                            !routeEvaluationComplete ||
                                routeEstimates.containsKey(selectedResult?.id)
                        ) &&
                        (
                            selectedResult?.let { routeEstimates[it.id] }
                                ?.let { it.addedDurationSeconds <= maximumDetourMinutes * 60.0 }
                                ?: true
                        ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            maximumViaPointsReached -> "Maximale Zahl an Zwischenstopps erreicht"
                            selectedResult != null -> "Ausgewählten Stopp einfügen"
                            else -> "Treffer auswählen"
                        }
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Schließen – Navigation läuft weiter")
                }
            }
        }
    }
}

private fun distanceFromRouteMeters(point: RoutePoint, route: List<RoutePoint>): Double {
    if (route.isEmpty()) return Double.POSITIVE_INFINITY
    if (route.size == 1) return distanceMeters(point, route.first())

    return route.zipWithNext().minOf { (start, end) ->
        val meanLatitude = Math.toRadians((start.latitude + end.latitude + point.latitude) / 3.0)
        val metersPerLongitude = 111_320.0 * kotlin.math.cos(meanLatitude)
        val metersPerLatitude = 111_132.0
        val px = (point.longitude - start.longitude) * metersPerLongitude
        val py = (point.latitude - start.latitude) * metersPerLatitude
        val dx = (end.longitude - start.longitude) * metersPerLongitude
        val dy = (end.latitude - start.latitude) * metersPerLatitude
        val denominator = dx * dx + dy * dy
        val fraction = if (denominator == 0.0) 0.0
        else ((px * dx + py * dy) / denominator).coerceIn(0.0, 1.0)
        kotlin.math.hypot(px - fraction * dx, py - fraction * dy)
    }
}

private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
    val meanLatitude = Math.toRadians((a.latitude + b.latitude) / 2.0)
    val dx = (a.longitude - b.longitude) * 111_320.0 * kotlin.math.cos(meanLatitude)
    val dy = (a.latitude - b.latitude) * 111_132.0
    return kotlin.math.hypot(dx, dy)
}
