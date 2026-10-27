package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationTrainingMetrics

@Composable
internal fun NavigationTrainingMetricsPanel(
    metrics: NavigationTrainingMetrics,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text =
                "KI-Training: " +
                    metrics.processedCases +
                    " Fälle verarbeitet",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text =
                "Bestanden " +
                    metrics.passedCases +
                    " · Fehlgeschlagen " +
                    metrics.failedCases +
                    " · Verbesserbar " +
                    metrics.improveableFailures,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text =
                "Trefferquote " +
                    percent(metrics.passRate) +
                    " · Lernpotenzial " +
                    percent(metrics.learnableRate) +
                    " · Ø " +
                    metrics.averageProcessingMs.toInt() +
                    " ms",
            style = MaterialTheme.typography.bodySmall,
        )

        if (metrics.capacityLimitCases > 0) {
            Text(
                text =
                    "Kapazitätsgrenze: " +
                        metrics.capacityLimitCases +
                        " Fälle",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (metrics.learnedCapabilities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text =
                    "Gelernt: " +
                        metrics.learnedCapabilities
                            .take(4)
                            .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        metrics.examples.take(2).forEach { example ->
            Text(
                text =
                    "Beispiel: " +
                        example.title +
                        " → " +
                        example.learned,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        metrics.recentFailures.take(2).forEach { failure ->
            Text(
                text =
                    "Offen: " +
                        failure.testId +
                        " · " +
                        failure.reason +
                        " → " +
                        failure.nextAction,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun percent(value: Double): String =
    (value * 100.0).toInt().toString() + "%"
