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
import org.routingplatform.app.profile.NavigationHapticIntensity
import org.routingplatform.app.profile.NavigationPreferences

@Composable
internal fun NavigationHapticSettingsDialog(
    preferences:
        NavigationPreferences,

    hapticAvailable:
        Boolean,

    onPreview:
        (NavigationHapticIntensity) ->
        Boolean,

    onSave:
        (NavigationPreferences) ->
        Unit,

    onDismiss:
        () -> Unit,
) {
    var enabled by
        remember(
            preferences
        ) {
            mutableStateOf(
                preferences
                    .hapticGuidanceEnabled
            )
        }

    var intensity by
        remember(
            preferences
        ) {
            mutableStateOf(
                preferences
                    .hapticIntensity
            )
        }

    var previewMessage by
        remember {
            mutableStateOf<String?>(
                null
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
                            .HapticSettingsDialog
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
                            "Haptische Führung",

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
                            12.dp
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
                                "Haptische Hinweise",

                            fontWeight =
                                FontWeight
                                    .SemiBold,
                        )

                        Text(
                            text =
                                if (
                                    enabled
                                ) {
                                    "Aktiv"
                                } else {
                                    "Aus"
                                },

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )
                    }

                    Switch(
                        checked =
                            enabled,

                        onCheckedChange = {
                                updated ->

                            enabled =
                                updated
                        },
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        if (
                            hapticAvailable
                        ) {
                            "Vibrator verfügbar"
                        } else {
                            "Auf diesem Gerät ist kein Vibrator verfügbar."
                        },

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            16.dp
                        )
                )

                Text(
                    text =
                        "Stärke",

                    fontWeight =
                        FontWeight
                            .SemiBold,
                )

                NavigationHapticIntensity
                    .entries
                    .forEach {
                            option ->

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .hapticIntensity(
                                                option.name
                                            )
                                    ),

                            onClick = {
                                intensity =
                                    option

                                previewMessage =
                                    null
                            },
                        ) {
                            Text(
                                text =
                                    (
                                        if (
                                            intensity ==
                                                option
                                        ) {
                                            "● "
                                        } else {
                                            "○ "
                                        }
                                    ) +
                                        navigationHapticIntensityLabel(
                                            option
                                        )
                            )
                        }
                    }

                Spacer(
                    modifier =
                        Modifier.height(
                            16.dp
                        )
                )

                Text(
                    text =
                        "Die Haptik ergänzt die sichtbare und gesprochene Navigation. " +
                            "Sie verändert weder Route noch Positionierung oder Sicherheitsgrenzen.",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

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
                                    .HapticPreview
                            ),

                    enabled =
                        hapticAvailable,

                    onClick = {
                        previewMessage =
                            if (
                                onPreview(
                                    intensity
                                )
                            ) {
                                "Haptik-Test ausgeführt."
                            } else {
                                "Haptik-Test konnte nicht gestartet werden."
                            }
                    },
                ) {
                    Text(
                        "Haptik testen"
                    )
                }

                previewMessage
                    ?.let {
                            message ->

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        Text(
                            text =
                                message,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )
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
                                    .HapticSave
                            ),

                    onClick = {
                        onSave(
                            preferences.copy(
                                hapticGuidanceEnabled =
                                    enabled,

                                hapticIntensity =
                                    intensity,
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

internal fun navigationHapticIntensityLabel(
    intensity:
        NavigationHapticIntensity,
): String =
    when (
        intensity
    ) {
        NavigationHapticIntensity.Gentle ->
            "Sanft"

        NavigationHapticIntensity.Standard ->
            "Standard"

        NavigationHapticIntensity.Strong ->
            "Stark"
    }