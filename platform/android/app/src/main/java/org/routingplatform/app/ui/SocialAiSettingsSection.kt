package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.routingplatform.app.profile.AiPreferences

internal object SocialAiUiTestTags {
    const val Section =
        "rp.social_ai.settings"

    const val AdaptationToggle =
        "rp.social_ai.adaptation"

    const val HumorSlider =
        "rp.social_ai.humor"

    const val CharmSlider =
        "rp.social_ai.charm"

    const val PlayfulnessSlider =
        "rp.social_ai.playfulness"

    const val ProactivitySlider =
        "rp.social_ai.proactivity"

    const val AdultFlirtOptIn =
        "rp.social_ai.adult_flirt_opt_in"

    const val FlirtSlider =
        "rp.social_ai.flirt"
}

@Composable
internal fun SocialAiSettingsSection(
    preferences: AiPreferences,
    onChanged: (AiPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(
                    SocialAiUiTestTags.Section
                ),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider()

        Text(
            text = "Soziale KI",
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text =
                "Humor, Charme und Eigeninitiative sind deine bewusste Auswahl. " +
                    "Gelernte Vorlieben bleiben davon getrennt.",
            style = MaterialTheme.typography.bodySmall,
        )

        SettingToggle(
            title = "Soziale Anpassung",
            description =
                "Darf Gesprächsstil und Eigeninitiative an Kontext und gelernte Vorlieben anpassen.",
            checked = preferences.socialAdaptationEnabled,
            testTag = SocialAiUiTestTags.AdaptationToggle,
            onCheckedChange = { enabled ->
                onChanged(
                    preferences.copy(
                        socialAdaptationEnabled = enabled
                    )
                )
            },
        )

        SocialLevelSlider(
            label = "Humor",
            value = preferences.humorLevel,
            testTag = SocialAiUiTestTags.HumorSlider,
            onValueChanged = { value ->
                onChanged(
                    preferences.copy(
                        humorLevel = value
                    )
                )
            },
        )

        SocialLevelSlider(
            label = "Charme",
            value = preferences.charmLevel,
            testTag = SocialAiUiTestTags.CharmSlider,
            onValueChanged = { value ->
                onChanged(
                    preferences.copy(
                        charmLevel = value
                    )
                )
            },
        )

        SocialLevelSlider(
            label = "Verspieltheit",
            value = preferences.playfulnessLevel,
            testTag = SocialAiUiTestTags.PlayfulnessSlider,
            onValueChanged = { value ->
                onChanged(
                    preferences.copy(
                        playfulnessLevel = value
                    )
                )
            },
        )

        SocialLevelSlider(
            label = "Eigeninitiative",
            value = preferences.proactivityLevel,
            testTag = SocialAiUiTestTags.ProactivitySlider,
            onValueChanged = { value ->
                onChanged(
                    preferences.copy(
                        proactivityLevel = value
                    )
                )
            },
        )

        HorizontalDivider()

        SettingToggle(
            title = "Flirtmodus für Erwachsene",
            description =
                "Nur nach deiner ausdrücklichen Aktivierung. " +
                    "Navigation und sicherheitskritische Hinweise bleiben davon ausgeschlossen.",
            checked = preferences.adultFlirtOptIn,
            testTag = SocialAiUiTestTags.AdultFlirtOptIn,
            onCheckedChange = { enabled ->
                onChanged(
                    preferences.copy(
                        adultFlirtOptIn = enabled
                    )
                )
            },
        )

        SocialLevelSlider(
            label = "Flirt",
            value = preferences.flirtLevel,
            enabled = preferences.adultFlirtOptIn,
            testTag = SocialAiUiTestTags.FlirtSlider,
            onValueChanged = { value ->
                onChanged(
                    preferences.copy(
                        flirtLevel = value
                    )
                )
            },
        )

        if (!preferences.adultFlirtOptIn) {
            Text(
                text =
                    "Flirt ist aus. Ein gespeicherter Wert wird ohne Adult-Opt-in nicht angewendet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1.0f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Switch(
            modifier = Modifier.testTag(testTag),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SocialLevelSlider(
    label: String,
    value: Int,
    testTag: String,
    enabled: Boolean = true,
    onValueChanged: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelLarge,
        )

        Slider(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .testTag(testTag),
            value = value.toFloat(),
            onValueChange = { raw ->
                onValueChanged(
                    raw.roundToInt().coerceIn(0, 100)
                )
            },
            enabled = enabled,
            valueRange = 0.0f..100.0f,
            steps = 99,
        )
    }
}
