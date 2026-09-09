package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.NavigationPreferences

@Composable
internal fun NavigationCriticalGuidanceSettingsDialog(
    preferences:
        NavigationPreferences,

    onSave:
        (NavigationPreferences) ->
        Unit,

    onDismiss:
        () -> Unit,
) {
    var repeatCriticalInstructions by
        remember(
            preferences
        ) {
            mutableStateOf(
                preferences
                    .repeatCriticalInstructions
            )
        }

    Dialog(
        onDismissRequest =
            onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.62f
                    )
                    .testTag(
                        NavigationUiTestTags
                            .CriticalGuidanceSettingsDialog
                    ),

            tonalElevation =
                8.dp,

            shape =
                MaterialTheme
                    .shapes
                    .large,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            16.dp
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement
                            .SpaceBetween,

                    verticalAlignment =
                        Alignment
                            .CenterVertically,
                ) {
                    Text(
                        text =
                            "Wichtige Hinweise",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,

                        fontWeight =
                            FontWeight
                                .Bold,
                    )

                    TextButton(
                        onClick =
                            onDismiss,
                    ) {
                        Text(
                            "Schließen"
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            16.dp
                        )
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement
                            .SpaceBetween,

                    verticalAlignment =
                        Alignment
                            .CenterVertically,
                ) {
                    Column {
                        Text(
                            text =
                                "Kritische Hinweise wiederholen",

                            fontWeight =
                                FontWeight
                                    .SemiBold,
                        )

                        Text(
                            text =
                                if (
                                    repeatCriticalInstructions
                                ) {
                                    "Aktiv"
                                } else {
                                    "Einmal"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )
                    }

                    Switch(
                        modifier =
                            Modifier.testTag(
                                NavigationUiTestTags
                                    .RepeatCriticalToggle
                            ),

                        checked =
                            repeatCriticalInstructions,

                        onCheckedChange = {
                                updated ->

                            repeatCriticalInstructions =
                                updated
                        },
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            20.dp
                        )
                )

                Text(
                    text =
                        "Bei U-Turns, Ausfahrten und Kreisverkehr-Manövern kann kurz vor dem Manöver " +
                            "ein zweiter Hinweis ausgegeben werden. Sprach- und Haptik-Ausgabe nutzen dieselbe Regel.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        "Die Wiederholung ist reine Präsentation. Route, Positionierung, Fortschritt, " +
                            "Rerouting, Routenauswahl und Sicherheitsgrenzen bleiben unverändert.",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            24.dp
                        )
                )

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .CriticalGuidanceSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                repeatCriticalInstructions =
                                    repeatCriticalInstructions
                            )
                        )

                        onDismiss()
                    },
                ) {
                    Text(
                        "Speichern"
                    )
                }
            }
        }
    }
}
