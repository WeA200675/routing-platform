package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.ProfileMapStyle

@Composable
internal fun MapStyleSettingsDialog(
    currentStyle: ProfileMapStyle,
    onSelect: (ProfileMapStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp)) {
                Text("Kartenprofil", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Terra zeigt eine naturnahe Outdoor-Karte ohne Höhenrelief. 3D ergänzt Gebäude, wenn die Kartendaten sie enthalten.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                val options = listOf(
                    ProfileMapStyle.Standard to ("Standard" to "Ausgewogene Straßenkarte"),
                    ProfileMapStyle.Terrain to ("Terra / Outdoor" to "Naturnahe Farbgebung"),
                    ProfileMapStyle.RoadOnly to ("Straßen pur" to "Reduzierte, helle Straßenkarte"),
                    ProfileMapStyle.ThreeDimensional to ("3D-Gebäude" to "Karte mit Gebäudeextrusionen"),
                    ProfileMapStyle.Minimal to ("Minimal" to "Helle, klare Karte"),
                    ProfileMapStyle.HighContrast to ("Hoher Kontrast" to "Stärker hervorgehobene Straßen"),
                    ProfileMapStyle.Night to ("Nacht" to "Dunkle Karte"),
                )
                options.forEach { (style, copy) ->
                    OutlinedButton(
                        onClick = { onSelect(style) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = (if (style == currentStyle) "✓ " else "") + copy.first,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(copy.second, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
