package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.routingplatform.app.profile.AiPreferences
import org.routingplatform.app.profile.ExperiencePackDefinition

internal object SocialAiUiTestTags {
    const val Section =
        "rp.social_ai.settings"

    const val AdaptationToggle =
        "rp.social_ai.adaptation"

    const val RouteLearningToggle =
        "rp.social_ai.route_learning"

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
    experiencePack: ExperiencePackDefinition? = null,
    onGenerateConversation: (String, (String?) -> Unit) -> Unit = { _, complete -> complete(null) },
) {
    var conversationInput by remember { mutableStateOf("") }
    var conversationReply by remember { mutableStateOf<String?>(null) }
    var conversationBusy by remember { mutableStateOf(false) }

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
            title = "Routen- und Themenlernen",
            description =
                "Lernt aus bestätigten positiven Routen- und Themenbewertungen, lokal auf diesem Gerät. " +
                    "Ziele, Koordinaten und rohe Fahrten werden nicht gespeichert; Ausschalten löscht diese Lernwerte.",
            checked = preferences.learningEnabled,
            testTag = SocialAiUiTestTags.RouteLearningToggle,
            onCheckedChange = { enabled ->
                onChanged(preferences.copy(learningEnabled = enabled))
            },
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
            title = "Erwachsenenmodus (18+)",
            description =
                "Bestätige, dass du mindestens 18 bist. Bei hohem Flirtwert darf die KI im Gespräch frech, derb und anzüglich werden. " +
                    "Navigation und sicherheitskritische Hinweise bleiben immer sachlich.",
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

        Text(
            text = "KI-Gespräch testen" + (experiencePack?.let { " · ${it.displayName}" } ?: ""),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Teste hier den tatsächlich installierten lokalen KI-Laufzeitpfad. Antworten bleiben außerhalb der Navigation.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = conversationInput,
            onValueChange = { conversationInput = it.take(1200); conversationReply = null },
            modifier = Modifier.fillMaxWidth().testTag("rp.social_ai.conversation_input"),
            label = { Text("Nachricht an die KI") },
            enabled = !conversationBusy,
            minLines = 2,
            maxLines = 4,
        )
        Button(
            onClick = {
                conversationBusy = true
                conversationReply = null
                onGenerateConversation(conversationInput) { reply ->
                    conversationReply = reply ?: "Keine lokale Antwort verfügbar. Prüfe, ob das KI-Modell installiert und bereit ist."
                    conversationBusy = false
                }
            },
            enabled = conversationInput.isNotBlank() && !conversationBusy,
            modifier = Modifier.testTag("rp.social_ai.conversation_test"),
        ) {
            Text(if (conversationBusy) "KI antwortet …" else "Gespräch ausprobieren")
        }
        conversationReply?.let { reply ->
            Text(reply, modifier = Modifier.testTag("rp.social_ai.conversation_reply"))
        }

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
