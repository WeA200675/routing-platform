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
import androidx.compose.material3.Slider
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
import kotlin.math.roundToInt
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

@Composable
internal fun NavigationVoiceSettingsDialog(
    preferences:
        VoicePreferences,

    personalityPreferences:
        NavigationPersonalityPreferences,

    catalogState:
        NavigationVoiceCatalogState,

    onPreview:
        (VoicePreferences) ->
        NavigationVoicePreviewResult,

    onSave:
        (VoicePreferences) -> Unit,

    onDismiss:
        () -> Unit,
) {
    var draft by
        remember(
            preferences
        ) {
            mutableStateOf(
                NavigationVoiceCatalog
                    .editablePreferences(
                        preferences
                    )
            )
        }

    var previewMessage by
        remember {
            mutableStateOf<String?>(
                null
            )
        }

    val readySnapshot =
        (
            catalogState as?
                NavigationVoiceCatalogState
                    .Ready
        )
            ?.snapshot

    val selectedLanguage =
        NavigationVoiceCatalog
            .languageOption(
                draft.languageTag
            )
            ?: NavigationVoiceCatalog
                .languageOptions
                .first()

    val selectedLanguageSupported =
        readySnapshot
            ?.supportsLanguage(
                selectedLanguage
                    .languageTag
            )
            ?: false

    val voices =
        readySnapshot
            ?.let {
                    snapshot ->

                NavigationVoiceCatalog
                    .voicesForLanguage(
                        snapshot =
                            snapshot,

                        languageTag =
                            selectedLanguage
                                .languageTag,
                    )
            }
            ?: emptyList()

    val effectiveDraft =
        ExperiencePackRuntimeResolver
            .resolveVoicePreferences(
                base =
                    draft,

                personality =
                    personalityPreferences,
            )

    Dialog(
        onDismissRequest =
            onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.92f
                    )
                    .testTag(
                        NavigationUiTestTags
                            .VoiceSettingsDialog
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
                            "Sprachführung",

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
                            8.dp
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
                                "Ansagen",

                            fontWeight =
                                FontWeight
                                    .SemiBold,
                        )

                        Text(
                            text =
                                if (
                                    draft.enabled
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
                            draft.enabled,

                        onCheckedChange = {
                                enabled ->

                            draft =
                                draft.copy(
                                    enabled =
                                        enabled
                                )
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
                        when (
                            catalogState
                        ) {
                            NavigationVoiceCatalogState
                                .Initializing ->
                                "Gerätestimmen werden geladen …"

                            NavigationVoiceCatalogState
                                .Unavailable ->
                                "TTS-Engine ist derzeit nicht verfügbar."

                            is NavigationVoiceCatalogState
                                .Ready ->
                                "Gerätestimmen bereit: " +
                                    catalogState
                                        .snapshot
                                        .voices
                                        .size +
                                    " Stimmen"
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
                        "Sprache",

                    fontWeight =
                        FontWeight
                            .SemiBold,
                )

                NavigationVoiceCatalog
                    .languageOptions
                    .forEach {
                            option ->

                        val supported =
                            readySnapshot
                                ?.supportsLanguage(
                                    option
                                        .languageTag
                                )
                                ?: false

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .voiceLanguage(
                                                option
                                                    .languageTag
                                            )
                                    ),

                            enabled =
                                supported,

                            onClick = {
                                draft =
                                    draft.copy(
                                        languageTag =
                                            option
                                                .languageTag,

                                        voiceId =
                                            null,
                                    )

                                previewMessage =
                                    null
                            },
                        ) {
                            Text(
                                text =
                                    (
                                        if (
                                            draft
                                                .languageTag
                                                .equals(
                                                    option
                                                        .languageTag,
                                                    ignoreCase =
                                                        true,
                                                )
                                        ) {
                                            "● "
                                        } else {
                                            "○ "
                                        }
                                    ) +
                                        option
                                            .displayName +
                                        if (
                                            readySnapshot !=
                                                null &&
                                            !supported
                                        ) {
                                            " (nicht verfügbar)"
                                        } else {
                                            ""
                                        }
                            )
                        }
                    }

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        "Stimme",

                    fontWeight =
                        FontWeight
                            .SemiBold,
                )

                TextButton(
                    modifier =
                        Modifier
                            .fillMaxWidth(),

                    enabled =
                        readySnapshot !=
                            null &&
                            selectedLanguageSupported,

                    onClick = {
                        draft =
                            draft.copy(
                                voiceId =
                                    null
                            )

                        previewMessage =
                            null
                    },
                ) {
                    Text(
                        text =
                            (
                                if (
                                    draft.voiceId ==
                                        null
                                ) {
                                    "● "
                                } else {
                                    "○ "
                                }
                            ) +
                                "Systemstimme"
                    )
                }

                voices
                    .forEach {
                            voice ->

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),

                            onClick = {
                                draft =
                                    draft.copy(
                                        voiceId =
                                            voice.id
                                    )

                                previewMessage =
                                    null
                            },
                        ) {
                            Text(
                                text =
                                    (
                                        if (
                                            draft.voiceId ==
                                                voice.id
                                        ) {
                                            "● "
                                        } else {
                                            "○ "
                                        }
                                    ) +
                                        NavigationVoiceCatalog
                                            .deviceVoiceLabel(
                                                voice
                                            )
                            )
                        }
                    }

                if (
                    readySnapshot !=
                        null &&
                    selectedLanguageSupported &&
                    voices.isEmpty()
                ) {
                    Text(
                        text =
                            "Keine explizite Voice-ID gemeldet. Die Systemstimme bleibt verfügbar.",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Text(
                    text =
                        "Android Voice stellt kein verlässliches, standardisiertes Geschlechtsmerkmal bereit. " +
                            "Eine sanfte oder weiblich klingende Stimme bitte über die Vorschau auswählen.",

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
                        "Sprechtempo: " +
                            speechRateLabel(
                                draft
                                    .speechRate
                            ),

                    fontWeight =
                        FontWeight
                            .SemiBold,
                )

                Slider(
                    value =
                        draft
                            .speechRate
                            .toFloat(),

                    onValueChange = {
                            raw ->

                        val rounded =
                            (
                                raw *
                                    10.0f
                            )
                                .roundToInt() /
                                10.0

                        draft =
                            draft.copy(
                                speechRate =
                                    rounded
                            )
                    },

                    valueRange =
                        0.5f..2.0f,

                    steps =
                        14,
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        "Ausführlichkeit",

                    fontWeight =
                        FontWeight
                            .SemiBold,
                )

                VoiceGuidanceVerbosity
                    .entries
                    .forEach {
                            verbosity ->

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth(),

                            onClick = {
                                draft =
                                    draft.copy(
                                        verbosity =
                                            verbosity
                                    )
                            },
                        ) {
                            Text(
                                text =
                                    (
                                        if (
                                            draft.verbosity ==
                                                verbosity
                                        ) {
                                            "● "
                                        } else {
                                            "○ "
                                        }
                                    ) +
                                        verbosityLabel(
                                            verbosity
                                        )
                            )
                        }
                    }

                if (
                    effectiveDraft
                        .verbosity !=
                    draft
                        .verbosity
                ) {
                    Text(
                        text =
                            "Der aktive Navi-Stil überschreibt die effektive Ausführlichkeit derzeit auf: " +
                                verbosityLabel(
                                    effectiveDraft
                                        .verbosity
                                ),

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
                                    .VoicePreview
                            ),

                    enabled =
                        readySnapshot !=
                            null &&
                            selectedLanguageSupported,

                    onClick = {
                        val previewPreferences =
                            readySnapshot
                                ?.let {
                                        snapshot ->

                                    NavigationVoiceCatalog
                                        .sanitizeVoiceSelection(
                                            preferences =
                                                draft,

                                            snapshot =
                                                snapshot,
                                        )
                                }
                                ?: draft

                        previewMessage =
                            when (
                                onPreview(
                                    previewPreferences
                                )
                            ) {
                                NavigationVoicePreviewResult
                                    .Spoken ->
                                    "Vorschau wird gesprochen."

                                NavigationVoicePreviewResult
                                    .NotReady ->
                                    "TTS-Engine ist noch nicht bereit."

                                NavigationVoicePreviewResult
                                    .Rejected ->
                                    "Vorschau konnte nicht gestartet werden."
                            }
                    },
                ) {
                    Text(
                        "Vorschau sprechen"
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
                            12.dp
                        )
                )

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .VoiceSave
                            ),

                    onClick = {
                        val persisted =
                            readySnapshot
                                ?.let {
                                        snapshot ->

                                    NavigationVoiceCatalog
                                        .sanitizeVoiceSelection(
                                            preferences =
                                                draft,

                                            snapshot =
                                                snapshot,
                                        )
                                }
                                ?: draft

                        onSave(
                            persisted
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

private fun speechRateLabel(
    speechRate:
        Double,
): String {
    val tenths =
        (
            speechRate *
                10.0
        )
            .roundToInt()

    return (
        tenths /
            10
    )
        .toString() +
        "." +
        (
            tenths %
                10
        ) +
        "x"
}

private fun verbosityLabel(
    verbosity:
        VoiceGuidanceVerbosity,
): String =
    when (
        verbosity
    ) {
        VoiceGuidanceVerbosity
            .Minimal ->
            "Kurz"

        VoiceGuidanceVerbosity
            .Standard ->
            "Standard"

        VoiceGuidanceVerbosity
            .Detailed ->
            "Ausführlich"
    }