package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.InformationDensityPreference

@Composable
internal fun NavigationInformationDensitySettingsDialog(
    preferences:
        DisplayPreferences,

    onSave:
        (DisplayPreferences) -> Unit,

    onDismiss:
        () -> Unit,
) {
    var selectedDensity by
        remember(preferences) {
            mutableStateOf(
                preferences.informationDensity
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
                    .fillMaxHeight(0.72f)
                    .testTag(
                        NavigationUiTestTags
                            .InformationDensitySettingsDialog
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
                        .padding(16.dp),
            ) {
                Text(
                    text =
                        "Informationsdichte",

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold,
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Bestimmt nur, wie viele bereits verfügbare Navigationsinformationen angezeigt werden. " +
                            "Routing, Fortschritt und Sicherheitslogik bleiben unverändert.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                INFORMATION_DENSITY_OPTIONS.forEach {
                        option ->

                    val active =
                        selectedDensity ==
                            option

                    TextButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .informationDensityOption(
                                            option.name
                                        )
                                ),

                        onClick = {
                            selectedDensity =
                                option
                        },
                    ) {
                        Text(
                            text =
                                NavigationInformationDensityPresentation
                                    .label(option) +
                                    if (active) {
                                        " · Aktiv"
                                    } else {
                                        ""
                                    }
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .InformationDensitySave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                informationDensity =
                                    selectedDensity
                            )
                        )

                        onDismiss()
                    },
                ) {
                    Text("Speichern")
                }

                TextButton(
                    modifier =
                        Modifier.fillMaxWidth(),

                    onClick =
                        onDismiss,
                ) {
                    Text("Schließen")
                }
            }
        }
    }
}

private val INFORMATION_DENSITY_OPTIONS =
    listOf(
        InformationDensityPreference.Minimal,
        InformationDensityPreference.Standard,
        InformationDensityPreference.Detailed,
    )