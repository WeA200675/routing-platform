package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.profile.ExperiencePackCatalog
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.WeeklyDiscoveryIntensity

@Composable
internal fun ExperiencePackDialog(
    preferences:
        NavigationPersonalityPreferences,

    onSelectPack:
        (String) -> Unit,

    onWeeklyDiscoveryChanged:
        (Boolean) -> Unit,

    onWeeklyDiscoveryIntensityChanged:
        (WeeklyDiscoveryIntensity) -> Unit,

    onDismiss:
        () -> Unit,
) {
    val selectedPack =
        ExperiencePackCatalog
            .require(
                preferences
                    .selectedPackId
            )

    val activePack =
        ExperiencePackRuntimeResolver
            .resolvePackOverride(
                personality =
                    preferences,
            )
            ?: selectedPack

    Dialog(
        onDismissRequest =
            onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.88f
                    )
                    .testTag(
                        NavigationUiTestTags
                            .ExperiencePackDialog
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
                        .fillMaxWidth()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            18.dp
                        ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "Dein Navi-Stil",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,

                        fontWeight =
                            FontWeight.Bold,
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

                Text(
                    text =
                        "Mach die Navigation zu deiner Navigation. " +
                            "Deine bewusste Auswahl bleibt immer maßgeblich.",

                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp
                        )
                )

                Text(
                    text =
                        "Aktiv: ${activePack.displayName}",

                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                )

                Text(
                    text =
                        activePack.description,

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

                ExperiencePackCatalog
                    .builtIns
                    .forEach {
                            pack ->

                        Button(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .experiencePack(
                                                pack.packId
                                            )
                                    ),

                            onClick = {
                                onSelectPack(
                                    pack.packId
                                )
                            },
                        ) {
                            Text(
                                text =
                                    if (
                                        pack.packId ==
                                            preferences
                                                .selectedPackId
                                    ) {
                                        "✓ ${pack.displayName}"
                                    } else {
                                        pack.displayName
                                    }
                            )
                        }

                        Text(
                            text =
                                pack.description,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    10.dp
                                )
                        )
                    }

                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Column(
                        modifier =
                            Modifier.weight(
                                1.0f
                            )
                    ) {
                        Text(
                            text =
                                "Wöchentlich überraschen",

                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium,
                        )

                        Text(
                            text =
                                "Jede Woche darf ein neuer Stil vorgeschlagen werden.",

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
                                    .WeeklyDiscoveryToggle
                            ),

                        checked =
                            preferences
                                .weeklyDiscoveryEnabled,

                        onCheckedChange =
                            onWeeklyDiscoveryChanged,
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
                        "Überraschungsstärke",

                    style =
                        MaterialTheme
                            .typography
                            .titleSmall,
                )

                WeeklyDiscoveryIntensity
                    .values()
                    .forEach {
                            intensity ->

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .weeklyIntensity(
                                                intensity.name
                                            )
                                    ),

                            enabled =
                                preferences
                                    .weeklyDiscoveryEnabled,

                            onClick = {
                                onWeeklyDiscoveryIntensityChanged(
                                    intensity
                                )
                            },
                        ) {
                            Text(
                                text =
                                    (
                                        if (
                                            intensity ==
                                                preferences
                                                    .weeklyDiscoveryIntensity
                                        ) {
                                            "✓ "
                                        } else {
                                            ""
                                        }
                                    ) +
                                        intensityLabel(
                                            intensity
                                        )
                            )
                        }
                    }

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Text(
                    text =
                        "Classic bleibt die sichere Standardbasis. " +
                            "Zen ist ruhiger. Galactic darf dich zum Schmunzeln bringen.",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )
            }
        }
    }
}

private fun intensityLabel(
    intensity:
        WeeklyDiscoveryIntensity,
): String =
    when (
        intensity
    ) {
        WeeklyDiscoveryIntensity.Subtle ->
            "Dezent"

        WeeklyDiscoveryIntensity.Creative ->
            "Kreativ"

        WeeklyDiscoveryIntensity.Wild ->
            "Wild"
    }