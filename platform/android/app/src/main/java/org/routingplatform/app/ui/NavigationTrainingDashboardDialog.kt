package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationTrainingMetrics
import org.routingplatform.app.navigation.NavigationTrainingReport

@Composable
internal fun NavigationTrainingDashboardDialog(
    metrics: NavigationTrainingMetrics,
    report: NavigationTrainingReport? = null,
    onDismiss: () -> Unit,
) {
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
            }
        },
    )
}
