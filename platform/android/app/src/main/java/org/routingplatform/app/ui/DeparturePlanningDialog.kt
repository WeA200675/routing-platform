package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.routingplatform.app.navigation.DepartureEstimateKind
import org.routingplatform.app.navigation.DepartureRecommendation
import org.routingplatform.app.navigation.DepartureRecommendationPolicy
import org.routingplatform.app.navigation.HistoricalRouteObservation
import org.routingplatform.app.navigation.HistoricalTrafficAverager
import org.routingplatform.app.navigation.PlannedArrivalParseResult
import org.routingplatform.app.navigation.PlannedArrivalTimeParser

@Composable
internal fun DeparturePlanningDialog(
    routeDurationSeconds: Int,
    historicalObservations: List<HistoricalRouteObservation>,
    onDismiss: () -> Unit,
) {
    var arrivalDateText by remember {
        mutableStateOf(LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMANY)))
    }
    var arrivalText by remember { mutableStateOf("08:00") }
    var result by remember { mutableStateOf<DepartureRecommendation?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val zone = ZoneId.systemDefault()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Abfahrtszeit planen", style = MaterialTheme.typography.titleLarge)
                Text("Ankunft bis ein Jahr im Voraus · 5 Minuten Puffer · Daten bleiben auf diesem Handy")
                OutlinedTextField(
                    value = arrivalDateText,
                    onValueChange = {
                        arrivalDateText = it.take(10)
                        result = null
                        error = null
                    },
                    label = { Text("Ankunftsdatum (TT.MM.JJJJ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("departure_arrival_date"),
                )
                OutlinedTextField(
                    value = arrivalText,
                    onValueChange = {
                        arrivalText = it.take(5)
                        result = null
                        error = null
                    },
                    label = { Text("Ankunft heute oder morgen (HH:mm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("departure_arrival_time"),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                result?.let { recommendation ->
                    val time = Instant.ofEpochMilli(recommendation.recommendedDepartureEpochMillis)
                        .atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY))
                    Text("Empfohlene Abfahrt: $time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (recommendation.estimateKind) {
                            DepartureEstimateKind.LiveTraffic ->
                                "Mit aktueller Verkehrsmeldung"
                            DepartureEstimateKind.HistoricalMean ->
                                "Mittelwert aus ${recommendation.historicalSampleCount} ähnlichen Fahrten"
                            DepartureEstimateKind.BaselineOnly ->
                                "Nur Routenzeit und Puffer · kein Livefeed und zu wenig Vergleichsdaten"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    modifier = Modifier.fillMaxWidth().testTag("departure_calculate"),
                    onClick = {
                        val now = System.currentTimeMillis()
                        val arrival = when (
                            val parsed = PlannedArrivalTimeParser.parse(
                                dateText = arrivalDateText,
                                timeText = arrivalText,
                                nowEpochMillis = now,
                                zoneId = zone,
                            )
                        ) {
                            is PlannedArrivalParseResult.Valid -> parsed.epochMillis
                            is PlannedArrivalParseResult.Invalid -> {
                                error = parsed.message
                                return@Button
                            }
                        }
                        val targetDeparture = (arrival - routeDurationSeconds.coerceAtLeast(1) * 1_000L - 300_000L)
                            .coerceAtLeast(now)
                        val routeKey = historicalObservations.firstOrNull()?.routeKey
                        val historical = routeKey?.let {
                            HistoricalTrafficAverager.estimateDelay(
                                observations = historicalObservations,
                                routeKey = it,
                                targetDepartureEpochMillis = targetDeparture,
                                zoneId = zone,
                            )
                        }
                        result = DepartureRecommendationPolicy.recommend(
                            arrivalEpochMillis = arrival,
                            routeDurationSeconds = routeDurationSeconds.coerceAtLeast(1),
                            safetyBufferSeconds = 300,
                            trafficReport = null,
                            nowEpochMillis = now,
                            historicalDelaySeconds = historical?.meanDelaySeconds,
                            historicalSampleCount = historical?.sampleCount ?: 0,
                        )
                        error = null
                    },
                ) { Text("Abfahrt berechnen") }
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}
