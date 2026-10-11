package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.InstructionLeadTimePreference
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference

@Composable
internal fun NavigationOnboardingDialog(
    driving: DrivingPreferences,
    navigation: NavigationPreferences,
    learningEnabled: Boolean,
    onSave: (DrivingPreferences, NavigationPreferences, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var draftDriving by remember(driving) { mutableStateOf(driving) }
    var draftNavigation by remember(navigation) { mutableStateOf(navigation) }
    var learn by remember(learningEnabled) { mutableStateOf(learningEnabled) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(20.dp).heightIn(max = 650.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Dein Navi einrichten", style = MaterialTheme.typography.titleLarge)
                Text("Wähle kurz aus, wie du am liebsten fährst und angesagt werden möchtest. Du kannst alles später ändern.")
                Text("Fahrstil", style = MaterialTheme.typography.titleMedium)
                OnboardingChoiceRow(listOf("Entspannt", "Ausgewogen", "Direkt"), draftDriving.style.ordinal) {
                    draftDriving = draftDriving.copy(style = DrivingStylePreference.values()[it])
                }
                Text("Routenart", style = MaterialTheme.typography.titleMedium)
                OnboardingChoiceRow(listOf("Ausgewogen", "Stabil", "Hauptstraßen", "Einfache Abbiegungen"), draftDriving.routeStyle.ordinal) {
                    draftDriving = draftDriving.copy(routeStyle = RouteStylePreference.values()[it])
                }
                Text("Ansagezeitpunkt", style = MaterialTheme.typography.titleMedium)
                OnboardingChoiceRow(listOf("Früh", "Standard", "Spät"), when (draftNavigation.instructionLeadTime) {
                    InstructionLeadTimePreference.Early -> 0
                    InstructionLeadTimePreference.Standard -> 1
                    InstructionLeadTimePreference.Late -> 2
                }) {
                    draftNavigation = draftNavigation.copy(instructionLeadTime = listOf(
                        InstructionLeadTimePreference.Early,
                        InstructionLeadTimePreference.Standard,
                        InstructionLeadTimePreference.Late,
                    )[it])
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = learn, onClick = { learn = !learn })
                    Text("Antworten nach Fahrten zum Lernen meiner Präferenzen verwenden")
                }
                Text("Lernen bleibt aus, solange du diese Option nicht aktivierst.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Später") }
                    Button(onClick = {
                        onSave(draftDriving, draftNavigation, learn)
                        onDismiss()
                    }) { Text("Speichern") }
                }
            }
        }
    }
}


@Composable
private fun OnboardingChoiceRow(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column {
        labels.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == index,
                    onClick = { onSelect(index) },
                )
                Text(label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
