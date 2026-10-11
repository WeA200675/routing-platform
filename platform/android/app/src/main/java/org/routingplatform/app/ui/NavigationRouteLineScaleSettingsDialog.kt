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
import kotlin.math.abs
import org.routingplatform.app.profile.DisplayPreferences

@Composable
internal fun NavigationRouteLineScaleSettingsDialog(
    preferences:
        DisplayPreferences,

    onSave:
        (DisplayPreferences) -> Unit,

    onDismiss:
        () -> Unit,
) {
    var selectedScale by
        remember(
            preferences
        ) {
            mutableStateOf(
                preferences.routeLineScale
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
                        0.78f
                    )
                    .testTag(
                        NavigationUiTestTags
                            .RouteLineScaleSettingsDialog
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
                Text(
                    text =
                        "Routenlinienbreite",

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.Bold,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Text(
                    text =
                        "Ändert nur die gezeichnete Breite der Routenlinie. " +
                            "Route, Geometrie, Fortschritt und Sicherheitslogik bleiben unverändert.",

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

                ROUTE_LINE_SCALE_PRESETS.forEach {
                        scale ->

                    val percent =
                        NavigationRouteLineScalePresentation
                            .percent(
                                scale
                            )

                    val active =
                        abs(
                            selectedScale -
                                scale
                        ) <
                            0.0001

                    TextButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .routeLineScalePreset(
                                            percent
                                        )
                                ),

                        onClick = {
                            selectedScale =
                                scale
                        },
                    ) {
                        Text(
                            text =
                                "$percent %" +
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
                        Modifier.height(
                            16.dp
                        )
                )

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .RouteLineScaleSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                routeLineScale =
                                    selectedScale
                            )
                        )

                        onDismiss()
                    },
                ) {
                    Text(
                        "Speichern"
                    )
                }

                TextButton(
                    modifier =
                        Modifier.fillMaxWidth(),

                    onClick =
                        onDismiss,
                ) {
                    Text(
                        "Schließen"
                    )
                }
            }
        }
    }
}

private val ROUTE_LINE_SCALE_PRESETS =
    listOf(
        0.75,
        1.0,
        1.25,
        1.5,
        1.75,
        2.0,
    )