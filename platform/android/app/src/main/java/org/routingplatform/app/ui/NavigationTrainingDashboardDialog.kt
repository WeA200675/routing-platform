package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationTrainingMetrics
import org.routingplatform.app.navigation.NavigationTrainingReport
import org.routingplatform.app.navigation.NavigationTrainingCase
import org.routingplatform.app.navigation.NavigationTrainingReviewState

@Composable
internal fun NavigationTrainingDashboardDialog(
    metrics: NavigationTrainingMetrics,
    report: NavigationTrainingReport? = null,
    onDismiss: () -> Unit,
    onCaseReviewed: (NavigationTrainingCase, NavigationTrainingReviewState) -> Unit = { _, _ -> },
) {
    val cases = report?.proposedCases.orEmpty()
    var caseIndex by remember(cases) { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        },
        title = {
            Text("KI-Trainingskontrolle")
        },
        text = {
            Column(
                modifier =
                    Modifier.verticalScroll(
                        rememberScrollState()
                    ),
            ) {
                Text(
                    text = "Verarbeitung",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        "Fälle: ${metrics.processedCases} · " +
                            "Bestanden: ${metrics.passedCases} · " +
                            "Fehlgeschlagen: ${metrics.failedCases}",
                )
                Text(
                    text =
                        "Verbesserbar: ${metrics.improveableFailures} · " +
                            "Kapazitätsgrenzen: ${metrics.capacityLimitCases}",
                )
                Text(
                    text =
                        "Trefferquote: ${(metrics.passRate * 100).toInt()} % · " +
                            "Ø ${metrics.averageProcessingMs.toInt()} ms",
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Was bereits gelernt wird",
                    style = MaterialTheme.typography.titleSmall,
                )
                metrics.learnedCapabilities.forEach { capability ->
                    Text(text = "• $capability")
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Trainingsbeispiele",
                    style = MaterialTheme.typography.titleSmall,
                )
                metrics.examples.forEach { example ->
                    Text(
                        text =
                            "${example.title}: ${example.input} → " +
                                "${example.learned}",
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Offene und verbesserbare Tests",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (metrics.recentFailures.isEmpty()) {
                    Text("Keine offenen Fehlerfälle.")
                } else {
                    metrics.recentFailures.forEach { failure ->
                        Text(
                            text =
                                "${failure.testId}: ${failure.reason} → " +
                                    "${failure.nextAction}",
                        )
                    }
                }

                if (cases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Vorgeschlagene Trainingsfälle", style = MaterialTheme.typography.titleSmall)
                    val current = cases[caseIndex.coerceIn(0, cases.lastIndex)]
                    Text("Fall ${caseIndex + 1} von ${cases.size} · ${current.priority} · ${current.category}")
                    Text("Eingabe: ${current.inputSummary}")
                    Text("Erwartet: ${current.expectedSummary}")
                    Text("Beobachtet: ${current.observedSummary}")
                    if (current.decisiveSignals.isNotEmpty()) Text("Signale: ${current.decisiveSignals.joinToString()}")
                    if (current.rejectedSignals.isNotEmpty()) Text("Verworfen: ${current.rejectedSignals.joinToString()}")
                    if (current.affectedCodeAreas.isNotEmpty()) Text("Code: ${current.affectedCodeAreas.joinToString()}")
                    if (current.suggestedTests.isNotEmpty()) Text("Tests: ${current.suggestedTests.joinToString()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { onCaseReviewed(current, NavigationTrainingReviewState.Accepted); if (caseIndex < cases.lastIndex) caseIndex++ }) { Text("Übernehmen") }
                        TextButton(onClick = { onCaseReviewed(current, NavigationTrainingReviewState.Deferred); if (caseIndex < cases.lastIndex) caseIndex++ }) { Text("Später") }
                        TextButton(onClick = { onCaseReviewed(current, NavigationTrainingReviewState.Rejected); if (caseIndex < cases.lastIndex) caseIndex++ }) { Text("Ablehnen") }
                    }
                }
            }
        },
    )
}
