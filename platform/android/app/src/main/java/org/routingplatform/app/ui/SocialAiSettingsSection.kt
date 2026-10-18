package org.routingplatform.app.ui

import android.content.Intent
import org.routingplatform.app.ai.SocialAiDeviceTestActivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.routingplatform.app.hub.AndroidAiHubClient
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
    val appContext = LocalContext.current.applicationContext
    val hubClient = remember(appContext) { AndroidAiHubClient(appContext) }
    val hubScope = rememberCoroutineScope()
    var hubUrl by remember { mutableStateOf(hubClient.savedUrl()) }
    var hubToken by remember { mutableStateOf("") }
    var hubConnected by remember { mutableStateOf(false) }
    var hubAiReady by remember { mutableStateOf(false) }
    var hubState by remember { mutableStateOf("Nicht verbunden") }
    var hubMessage by remember { mutableStateOf("") }
    var hubReply by remember { mutableStateOf<String?>(null) }
    var hubRun by remember { mutableStateOf<AndroidAiHubClient.TrainingRun?>(null) }
    var hubPreset by remember { mutableStateOf("speech_clarity") }
    var hubMenuOpen by remember { mutableStateOf(false) }
    var hubBusy by remember { mutableStateOf(false) }

    fun refreshHub() {
        hubScope.launch {
            try {
                val status = withContext(Dispatchers.IO) { hubClient.status() }
                hubConnected = true
                hubAiReady = status.ready
                hubState = if (status.ready) "KI konfiguriert · ${status.model}" else "Hub verbunden, KI-Anbieter fehlt"
                hubRun = withContext(Dispatchers.IO) { hubClient.lastTrainingRun() }
            } catch (error: Exception) {
                hubConnected = false
                hubAiReady = false
                hubState = error.message ?: "Verbindung fehlgeschlagen"
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hubClient.savedUrl().isNotBlank()) refreshHub()
    }
    LaunchedEffect(hubConnected, hubRun?.id, hubRun?.status) {
        while (isActive && hubClient.savedUrl().isNotBlank()) {
            delay(if (hubConnected && (hubRun?.status == "queued" || hubRun?.status == "running")) 8_000 else if (hubConnected) 30_000 else 10_000)
            try {
                val status = withContext(Dispatchers.IO) { hubClient.status() }
                hubConnected = true
                hubAiReady = status.ready
                hubState = if (status.ready) "Hub erreichbar · KI konfiguriert · ${status.model}" else "Hub verbunden, KI-Anbieter fehlt"
                val current = hubRun
                if (current?.status == "queued" || current?.status == "running") {
                    hubRun = withContext(Dispatchers.IO) { hubClient.trainingRun(current.id) }
                }
            } catch (_: Exception) {
                hubConnected = false
                hubAiReady = false
                hubState = "Verbindung unterbrochen · erneut verbinden"
            }
        }
    }


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

        Text(
            text = "Lokaler KI-Gerätetest",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Prüft das installierte Modell und die native Laufzeit direkt auf diesem Handy. Der Test läuft lokal und bleibt getrennt von der Navigation.",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = {
                appContext.startActivity(
                    Intent(appContext, SocialAiDeviceTestActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth().testTag("rp.social_ai.device_test_open"),
        ) {
            Text("KI-Gerätetest öffnen")
        }

        SettingToggle(
            title = "Routen- und Themenlernen",
            description =
                "Lernt ausschließlich lokal aus bestätigten Routenbewertungen und deinen 👍/👎 für Zwischenziel-Kategorien. " +
                    "Es speichert keine Ziele, Koordinaten oder Fahrten; Ausschalten löscht diese Lernwerte.",
            checked = preferences.learningEnabled,
            testTag = SocialAiUiTestTags.RouteLearningToggle,
            onCheckedChange = { enabled ->
                onChanged(preferences.copy(learningEnabled = enabled))
            },
        )

        Text(
            text = "Lokales Lernen: Deine Zwischenziel-Bewertungen aktualisieren direkt ein kleines Bayes-Präferenzmodell auf dem Gerät. Es sortiert passende Kategorien künftig nach deinen bisherigen Bewertungen. Das Qwen-Sprachmodell selbst wird dadurch nicht feinabgestimmt; Navigationsentscheidungen bleiben bei der geprüften Routing-Engine.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("rp.social_ai.local_learning_explanation"),
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
            text = "Dieser Test nutzt ausschließlich die lokale KI. Wenn Modell oder Laufzeit fehlen, erscheint ein lokaler Fehler; es gibt keinen automatischen Hub-Fallback. Antworten bleiben außerhalb der Navigation.",
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
                    if (reply != null) {
                        conversationReply = reply
                        conversationBusy = false
                    } else {
                        conversationReply = "Lokale KI nicht verfügbar. Prüfe, ob das signierte lokale Kandidaten-APK mit Modell und Laufzeit installiert ist. Es wurden keine Gesprächsdaten an einen Hub gesendet."
                        conversationBusy = false
                    }
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

        HorizontalDivider()
        Text("KI-Hub · wiederverbindbare Verbindung", style = MaterialTheme.typography.titleSmall)
        Text(
            "Verbinde die App sicher mit deinem privaten Hub. Der Token wird verschlüsselt im Android Keystore abgelegt. KI-Gespräche bleiben getrennt von der sicherheitskritischen Routenführung.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = hubUrl,
            onValueChange = { hubUrl = it.take(500) },
            modifier = Modifier.fillMaxWidth().testTag("rp.social_ai.hub_url"),
            label = { Text("HTTPS-Adresse des privaten KI-Hubs") },
            enabled = !hubBusy,
            singleLine = true,
        )
        OutlinedTextField(
            value = hubToken,
            onValueChange = { hubToken = it.take(512) },
            modifier = Modifier.fillMaxWidth().testTag("rp.social_ai.hub_token"),
            label = { Text("Hub-Zugriffstoken") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !hubBusy,
            singleLine = true,
        )
        Text(hubState, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("rp.social_ai.hub_status"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !hubBusy && hubUrl.isNotBlank() && hubToken.length >= 32,
                onClick = {
                    hubBusy = true
                    hubScope.launch {
                        try {
                            withContext(Dispatchers.IO) { hubClient.saveConnection(hubUrl, hubToken) }
                            hubToken = ""
                            refreshHub()
                        } catch (error: Exception) {
                            hubState = error.message ?: "Verbindung fehlgeschlagen"
                        } finally { hubBusy = false }
                    }
                },
                modifier = Modifier.testTag("rp.social_ai.hub_connect"),
            ) { Text(if (hubBusy) "Verbinde …" else "Sicher verbinden") }
            OutlinedButton(enabled = hubConnected && !hubBusy, onClick = { refreshHub() }) { Text("Status prüfen") }
        }
        if (hubConnected) {
            OutlinedTextField(
                value = hubMessage,
                onValueChange = { hubMessage = it.take(1200); hubReply = null },
                modifier = Modifier.fillMaxWidth().testTag("rp.social_ai.hub_chat_input"),
                label = { Text("Nachricht an die verbundene KI") },
                enabled = !hubBusy && hubAiReady,
                minLines = 2,
                maxLines = 4,
            )
            Button(
                enabled = hubAiReady && !hubBusy && hubMessage.isNotBlank(),
                onClick = {
                    hubBusy = true
                    hubReply = null
                    hubScope.launch {
                        try { hubReply = withContext(Dispatchers.IO) { hubClient.chat(hubMessage) } }
                        catch (error: Exception) { hubReply = error.message ?: "KI momentan nicht erreichbar" }
                        finally { hubBusy = false }
                    }
                },
                modifier = Modifier.testTag("rp.social_ai.hub_chat_send"),
            ) { Text(if (hubBusy) "KI antwortet …" else "Mit Hub-KI sprechen") }
            hubReply?.let { Text(it, modifier = Modifier.testTag("rp.social_ai.hub_chat_reply")) }
            HorizontalDivider()
            Text("Optionales LoRA-Training auf dem Hub", style = MaterialTheme.typography.titleSmall)
            Text(
                "Diese getrennte Funktion startet echtes LoRA-Training auf dem Hub-Rechner, nicht auf dem Handy. Für lokales Lernen nutze die Zwischenziel-Bewertungen oben; sie aktualisieren das kleine Präferenzmodell auf diesem Gerät.",
                style = MaterialTheme.typography.bodySmall,
            )
            Box {
                OutlinedButton(onClick = { hubMenuOpen = true }, enabled = !hubBusy) {
                    Text(mapOf("speech_clarity" to "Sprachklarheit", "route_explanation" to "Routenbegründungen",
                        "traffic_summary" to "Stauzusammenfassungen", "hazard_summary" to "Gefahrenhinweise",
                        "gps_confidence" to "GPS-Konfidenz")[hubPreset] ?: hubPreset)
                }
                DropdownMenu(expanded = hubMenuOpen, onDismissRequest = { hubMenuOpen = false }) {
                    listOf("speech_clarity" to "Sprachklarheit", "route_explanation" to "Routenbegründungen",
                        "traffic_summary" to "Stauzusammenfassungen", "hazard_summary" to "Gefahrenhinweise",
                        "gps_confidence" to "GPS-Konfidenz").forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { hubPreset = key; hubMenuOpen = false })
                    }
                }
            }
            Button(
                enabled = !hubBusy && hubRun?.status != "queued" && hubRun?.status != "running",
                onClick = {
                    hubBusy = true
                    hubScope.launch {
                        try { hubRun = withContext(Dispatchers.IO) { hubClient.startTraining("Handy-LoRA-${hubPreset}", hubPreset) } }
                        catch (error: Exception) { hubState = error.message ?: "Trainingsstart fehlgeschlagen" }
                        finally { hubBusy = false }
                    }
                },
                modifier = Modifier.testTag("rp.social_ai.hub_training_start"),
            ) { Text(if (hubBusy) "Wird gestartet …" else "Externes Hub-Training starten") }
            hubRun?.let { run ->
                Text("${run.status.uppercase()} · ${run.progress}% · ${run.stage}" +
                    if (run.error.isNotBlank()) " · ${run.error}" else "",
                    modifier = Modifier.testTag("rp.social_ai.hub_training_status"))
            }
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
