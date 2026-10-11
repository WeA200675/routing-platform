package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.DisplayPreferences

@Composable
internal fun NavigationAccentColorSettingsDialog(
    preferences:
        DisplayPreferences,

    onSave:
        (DisplayPreferences) -> Unit,

    onDismiss:
        () -> Unit,
) {
    var selectedColor by
        remember(
            preferences.accentColor
        ) {
            mutableStateOf(
                preferences.accentColor
            )
        }

    val dark =
        MaterialTheme
            .colorScheme
            .surface
            .luminance() <
            0.5f

    Dialog(
        onDismissRequest =
            onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.90f
                    )
                    .semantics {
                        testTagsAsResourceId =
                            true
                    }
                    .testTag(
                        NavigationUiTestTags
                            .AccentColorSettingsDialog
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
                        .padding(
                            16.dp
                        )
                        .verticalScroll(
                            rememberScrollState()
                        ),

                verticalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    ),
            ) {
                Text(
                    text =
                        "Akzentfarbe",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                )

                Text(
                    text =
                        "Ändert hervorgehobene Bedien- und Menütexte. " +
                            "Normaler Lesetext behält seine kontrastoptimierte Farbe.",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                NavigationAccentColorPresentation
                    .presets
                    .forEachIndexed {
                            index,
                            option ->

                        val optionColor =
                            NavigationAccentColorPresentation
                                .tileColor(
                                    preference =
                                        option,

                                    index =
                                        index,

                                    dark =
                                        dark,

                                    fallback =
                                        MaterialTheme
                                            .colorScheme
                                            .primary,
                                )

                        OutlinedButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .accentColorOption(
                                                option.name
                                            )
                                    ),

                            colors =
                                ButtonDefaults
                                    .outlinedButtonColors(
                                        contentColor =
                                            optionColor
                                    ),

                            onClick = {
                                selectedColor =
                                    option
                            },
                        ) {
                            Text(
                                text =
                                    if (
                                        selectedColor ==
                                            option
                                    ) {
                                        "✓ " +
                                            NavigationAccentColorPresentation
                                                .label(
                                                    option
                                                )
                                    } else {
                                        NavigationAccentColorPresentation
                                            .label(
                                                option
                                            )
                                    }
                            )
                        }
                    }

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .AccentColorSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                accentColor =
                                    selectedColor
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