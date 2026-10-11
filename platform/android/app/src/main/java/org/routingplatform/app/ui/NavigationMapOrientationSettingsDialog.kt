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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.ProfileMapOrientation

@Composable
internal fun NavigationMapOrientationSettingsDialog(
    preferences:
        DisplayPreferences,

    onSave:
        (DisplayPreferences) -> Unit,

    onDismiss:
        () -> Unit,
) {
    var selectedOrientation by
        remember(preferences) {
            mutableStateOf(
                preferences.mapOrientation
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
                    .semantics {
                        testTagsAsResourceId =
                            true
                    }
                    .testTag(
                        NavigationUiTestTags
                            .MapOrientationSettingsDialog
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
                        "Kartenausrichtung",

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
                        "Legt nur fest, ob die Karte der Fahrtrichtung folgt " +
                            "oder nach Norden ausgerichtet bleibt. Routing, " +
                            "Positionierung, Fortschritt und Sicherheitslogik " +
                            "bleiben unverändert.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                MAP_ORIENTATION_OPTIONS.forEach {
                        option ->

                    val active =
                        selectedOrientation ==
                            option

                    TextButton(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .mapOrientationOption(
                                            option.name
                                        )
                                ),

                        onClick = {
                            selectedOrientation =
                                option
                        },
                    ) {
                        Text(
                            text =
                                navigationMapOrientationLabel(
                                    option
                                ) +
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
                                    .MapOrientationSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                mapOrientation =
                                    selectedOrientation
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

internal fun navigationMapOrientationLabel(
    orientation:
        ProfileMapOrientation,
): String =
    when (orientation) {
        ProfileMapOrientation.HeadingUp ->
            "Fahrtrichtung oben"

        ProfileMapOrientation.NorthUp ->
            "Norden oben"
    }

private val MAP_ORIENTATION_OPTIONS =
    listOf(
        ProfileMapOrientation.HeadingUp,
        ProfileMapOrientation.NorthUp,
    )
