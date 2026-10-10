package org.routingplatform.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.DrivingPreferences
import org.routingplatform.app.profile.DrivingStylePreference
import org.routingplatform.app.profile.RouteStabilityPreference
import org.routingplatform.app.profile.RouteStylePreference

@Composable
internal fun DrivingPreferencesSettingsDialog(
    preferences: DrivingPreferences,
    onSave: (DrivingPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(preferences) { mutableStateOf(preferences) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fahrpräferenzen", style = MaterialTheme.typography.titleLarge)
                Text("Diese Auswahl wird für neue Routenvorschauen verwendet.")
                Text("Fahrstil", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("Entspannt", "Ausgewogen", "Direkt"), draft.style.ordinal) {
                    draft = draft.copy(style = DrivingStylePreference.values()[it])
                }
                Text("Routenart", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("Ausgewogen", "Stabil", "Hauptstraßen", "Einfache Manöver"), draft.routeStyle.ordinal) {
                    draft = draft.copy(routeStyle = RouteStylePreference.values()[it])
                }
                Text("Neuberechnung", style = MaterialTheme.typography.titleMedium)
                ChoiceRow(listOf("Reaktionsschnell", "Ausgewogen", "Stabil"), draft.routeStability.ordinal) {
                    draft = draft.copy(routeStability = RouteStabilityPreference.values()[it])
                }
                PreferenceSwitch("Hauptstraßen bevorzugen", draft.preferMajorRoads) {
                    draft = draft.copy(preferMajorRoads = it)
                }
                PreferenceSwitch("Komplexe Abbiegungen vermeiden", draft.avoidComplexTurns) {
                    draft = draft.copy(avoidComplexTurns = it)
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = { onSave(draft); onDismiss() }) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == index, onClick = { onSelect(index) })
                Text(label)
            }
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
