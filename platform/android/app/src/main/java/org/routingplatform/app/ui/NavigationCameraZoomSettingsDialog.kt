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
internal fun NavigationCameraZoomSettingsDialog(
    preferences:
        NavigationPreferences,

    onSave:
        (NavigationPreferences) ->
        Unit,

    onDismiss:
        () -> Unit,
) {
    var automaticMapZoom by
        remember(
            preferences
        ) {
            mutableStateOf(
                preferences
                    .automaticMapZoom
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
                        0.58f
                    )
                    .testTag(
                        NavigationUiTestTags
                            .CameraZoomSettingsDialog
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
                            "Kartenzoom",

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
                                "Automatischer Kartenzoom",

                            fontWeight =
                                FontWeight
                                    .SemiBold,
                        )

                        Text(
                            text =
                                if (
                                    automaticMapZoom
                                ) {
                                    "Aktiv"
                                } else {
                                    "Fester Profil-Zoom"
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
                                    .CameraAutoZoomToggle
                            ),

                        checked =
                            automaticMapZoom,

                        onCheckedChange = {
                                updated ->

                            automaticMapZoom =
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
                        "Beim Navigieren wird die Kartenkamera vor Manövern schrittweise näher herangeführt. " +
                            "Ist die Option aus, bleibt der im Profil gespeicherte Zoom fest.",

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
                        "Die Funktion verändert ausschließlich die Kartenpräsentation. " +
                            "Route, Positionierung, Fortschritt, Rerouting und Sicherheitsgrenzen bleiben unverändert.",

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
                                    .CameraZoomSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                automaticMapZoom =
                                    automaticMapZoom
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
