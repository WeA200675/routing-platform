package org.routingplatform.app.ui

import java.util.Locale
import org.routingplatform.app.profile.VoicePreferences

internal const val NAVIGATION_VOICE_DEFAULT_LANGUAGE_TAG =
    "de-DE"

internal data class NavigationDeviceVoice(
    val id: String,
    val languageTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
    val features: Set<String>,
)

internal data class NavigationVoiceLanguageOption(
    val languageTag: String,
    val displayName: String,
    val previewText: String,
)

internal data class NavigationVoiceCatalogSnapshot(
    val supportedLanguageTags:
        Set<String>,

    val voices:
        List<NavigationDeviceVoice>,
) {
    fun supportsLanguage(
        languageTag:
            String,
    ): Boolean =
        supportedLanguageTags
            .any {
                it.equals(
                    languageTag,
                    ignoreCase = true,
                )
            }
}

internal sealed interface NavigationVoiceCatalogState {
    object Initializing :
        NavigationVoiceCatalogState

    data class Ready(
        val snapshot:
            NavigationVoiceCatalogSnapshot,
    ) :
        NavigationVoiceCatalogState

    object Unavailable :
        NavigationVoiceCatalogState
}

internal enum class NavigationVoicePreviewResult {
    Spoken,
    NotReady,
    Rejected,
}

/*
 * Pure G6.9 presentation catalog.
 *
 * Android Voice exposes locale, quality, latency, network requirement,
 * feature flags and a stable voice name. It does not expose a portable,
 * standardized gender attribute. Selection therefore remains capability-
 * based and preview-driven instead of inventing a gender classification.
 *
 * This catalog has no routing, progress, positioning, rerouting or safety
 * authority.
 */
internal object NavigationVoiceCatalog {
    val languageOptions:
        List<NavigationVoiceLanguageOption> =
        listOf(
            NavigationVoiceLanguageOption(
                languageTag =
                    "de-DE",
                displayName =
                    "Deutsch (Deutschland)",
                previewText =
                    "In 200 Metern rechts abbiegen.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "en-GB",
                displayName =
                    "English (United Kingdom)",
                previewText =
                    "In 200 metres, turn right.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "en-US",
                displayName =
                    "English (United States)",
                previewText =
                    "In 600 feet, turn right.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "fr-FR",
                displayName =
                    "Français (France)",
                previewText =
                    "Dans 200 mètres, tournez à droite.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "it-IT",
                displayName =
                    "Italiano (Italia)",
                previewText =
                    "Tra 200 metri, svolta a destra.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "es-ES",
                displayName =
                    "Español (España)",
                previewText =
                    "En 200 metros, gira a la derecha.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "nl-NL",
                displayName =
                    "Nederlands (Nederland)",
                previewText =
                    "Sla over 200 meter rechtsaf.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "pl-PL",
                displayName =
                    "Polski (Polska)",
                previewText =
                    "Za 200 metrów skręć w prawo.",
            ),
            NavigationVoiceLanguageOption(
                languageTag =
                    "cs-CZ",
                displayName =
                    "Čeština (Česko)",
                previewText =
                    "Za 200 metrů odbočte doprava.",
            ),
        )

    fun languageOption(
        languageTag:
            String,
    ): NavigationVoiceLanguageOption? =
        languageOptions
            .firstOrNull {
                it.languageTag
                    .equals(
                        languageTag,
                        ignoreCase = true,
                    )
            }

    fun languageDisplayName(
        languageTag:
            String,
    ): String =
        languageOption(
            languageTag
        )
            ?.displayName
            ?: languageTag

    fun previewText(
        languageTag:
            String,
    ): String =
        languageOption(
            languageTag
        )
            ?.previewText
            ?: languageOptions
                .first {
                    it.languageTag ==
                        NAVIGATION_VOICE_DEFAULT_LANGUAGE_TAG
                }
                .previewText

    fun editablePreferences(
        preferences:
            VoicePreferences,
    ): VoicePreferences =
        if (
            languageOption(
                preferences
                    .languageTag
            ) !=
            null
        ) {
            preferences
        } else {
            preferences.copy(
                languageTag =
                    NAVIGATION_VOICE_DEFAULT_LANGUAGE_TAG,
                voiceId =
                    null,
            )
        }

    fun voicesForLanguage(
        snapshot:
            NavigationVoiceCatalogSnapshot,

        languageTag:
            String,
    ): List<NavigationDeviceVoice> =
        snapshot
            .voices
            .filter {
                it.id
                    .isNotBlank() &&
                    it.id.length <=
                    128 &&
                    voiceMatchesLanguageTag(
                        voiceLanguageTag =
                            it.languageTag,

                        targetLanguageTag =
                            languageTag,
                    )
            }
            .sortedWith(
                compareBy<NavigationDeviceVoice> {
                    it.requiresNetwork
                }
                    .thenByDescending {
                        it.quality
                    }
                    .thenBy {
                        it.latency
                    }
                    .thenBy {
                        it.id
                    }
            )

    fun sanitizeVoiceSelection(
        preferences:
            VoicePreferences,

        snapshot:
            NavigationVoiceCatalogSnapshot,
    ): VoicePreferences {
        val requestedVoiceId =
            preferences
                .voiceId
                ?: return preferences

        val available =
            voicesForLanguage(
                snapshot =
                    snapshot,

                languageTag =
                    preferences
                        .languageTag,
            )
                .any {
                    it.id ==
                        requestedVoiceId
                }

        return if (available) {
            preferences
        } else {
            preferences.copy(
                voiceId =
                    null
            )
        }
    }

    fun voiceMatchesLanguageTag(
        voiceLanguageTag:
            String,

        targetLanguageTag:
            String,
    ): Boolean {
        val voiceLocale =
            Locale.forLanguageTag(
                voiceLanguageTag
            )

        val targetLocale =
            Locale.forLanguageTag(
                targetLanguageTag
            )

        if (
            voiceLocale
                .language
                .isBlank() ||
            targetLocale
                .language
                .isBlank() ||
            !voiceLocale
                .language
                .equals(
                    targetLocale
                        .language,
                    ignoreCase = true,
                )
        ) {
            return false
        }

        /*
         * A generic engine voice such as "en" may legitimately serve
         * both en-GB and en-US. Country-specific voices remain scoped
         * to their own country.
         */
        return voiceLocale
            .country
            .isBlank() ||
            targetLocale
                .country
                .isBlank() ||
            voiceLocale
                .country
                .equals(
                    targetLocale
                        .country,
                    ignoreCase = true,
                )
    }

    fun deviceVoiceLabel(
        voice:
            NavigationDeviceVoice,
    ): String =
        voice.id +
            " | " +
            if (
                voice.requiresNetwork
            ) {
                "Netzwerk"
            } else {
                "Offline"
            } +
            " | Q " +
            voice.quality +
            " | L " +
            voice.latency
}