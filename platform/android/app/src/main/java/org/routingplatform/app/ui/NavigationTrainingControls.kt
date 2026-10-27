package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.routingplatform.app.navigation.NavigationIncidentTrigger
import org.routingplatform.app.navigation.NavigationTrainingMode
import org.routingplatform.app.navigation.NavigationTrainingPurpose

@Composable
internal fun NavigationTrainingControls(
    mode: NavigationTrainingMode,
    onTrainingModeChanged: (NavigationTrainingMode) -> Unit,
    onIncident: (NavigationIncidentTrigger) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(
            androidx.compose.ui.unit.dp(8),
        ),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                onTrainingModeChanged(
                    if (mode.active) {
                        mode.stop()
                    } else {
                        mode.start(
                            System.nanoTime(),
                            NavigationTrainingPurpose.GeneralModelTraining,
                        )
                    },
                )
            },
        ) {
            Text(if (mode.active) "Training stoppen" else "Training")
        }

        Button(
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(),
            onClick = {
                onIncident(NavigationIncidentTrigger.SevereNavigation)
                onTrainingModeChanged(mode.recordIncident())
            },
        ) {
            Text("Sehr schlimm")
        }

        Button(
            modifier = Modifier.weight(1f),
            onClick = {
                onIncident(NavigationIncidentTrigger.NavigationAborted)
                onTrainingModeChanged(mode.recordIncident())
            },
        ) {
            Text("Abbruch")
        }
    }
}
