package org.routingplatform.app.ui

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.profile.VoicePreferences

class NavigationVoiceCatalogTest {

    @Test
    fun productLanguageCatalogIsStable() {
        assertEquals(
            listOf(
                "de-DE",
                "en-GB",
                "en-US",
                "fr-FR",
                "it-IT",
                "es-ES",
                "nl-NL",
                "pl-PL",
                "cs-CZ",
            ),
            NavigationVoiceCatalog
                .languageOptions
                .map {
                    it.languageTag
                },
        )
    }

    @Test
    fun everyProductLanguageHasPreviewText() {
        assertTrue(
            NavigationVoiceCatalog
                .languageOptions
                .all {
                    it.previewText
                        .isNotBlank()
                }
        )
    }

    @Test
    fun countrySpecificEnglishVoicesStaySeparated() {
        assertTrue(
            NavigationVoiceCatalog
                .voiceMatchesLanguageTag(
                    voiceLanguageTag =
                        "en-GB",

                    targetLanguageTag =
                        "en-GB",
                )
        )

        assertFalse(
            NavigationVoiceCatalog
                .voiceMatchesLanguageTag(
                    voiceLanguageTag =
                        "en-GB",

                    targetLanguageTag =
                        "en-US",
                )
        )
    }

    @Test
    fun genericLanguageVoiceCanServeMatchingLanguage() {
        assertTrue(
            NavigationVoiceCatalog
                .voiceMatchesLanguageTag(
                    voiceLanguageTag =
                        "en",

                    targetLanguageTag =
                        "en-US",
                )
        )

        assertFalse(
            NavigationVoiceCatalog
                .voiceMatchesLanguageTag(
                    voiceLanguageTag =
                        "de",

                    targetLanguageTag =
                        "en-US",
                )
        )
    }

    @Test
    fun voiceOrderingPrefersOfflineThenQualityThenLatency() {
        val snapshot =
            NavigationVoiceCatalogSnapshot(
                supportedLanguageTags =
                    setOf(
                        "de-DE"
                    ),

                voices =
                    listOf(
                        voice(
                            id =
                                "network-best",
                            quality =
                                500,
                            latency =
                                100,
                            requiresNetwork =
                                true,
                        ),
                        voice(
                            id =
                                "offline-low",
                            quality =
                                200,
                            latency =
                                100,
                            requiresNetwork =
                                false,
                        ),
                        voice(
                            id =
                                "offline-high-slow",
                            quality =
                                400,
                            latency =
                                400,
                            requiresNetwork =
                                false,
                        ),
                        voice(
                            id =
                                "offline-high-fast",
                            quality =
                                400,
                            latency =
                                100,
                            requiresNetwork =
                                false,
                        ),
                    ),
            )

        assertEquals(
            listOf(
                "offline-high-fast",
                "offline-high-slow",
                "offline-low",
                "network-best",
            ),
            NavigationVoiceCatalog
                .voicesForLanguage(
                    snapshot =
                        snapshot,

                    languageTag =
                        "de-DE",
                )
                .map {
                    it.id
                },
        )
    }

    @Test
    fun missingExplicitVoiceFallsBackToSystemVoice() {
        val preferences =
            VoicePreferences(
                languageTag =
                    "de-DE",

                voiceId =
                    "missing",
            )

        val sanitized =
            NavigationVoiceCatalog
                .sanitizeVoiceSelection(
                    preferences =
                        preferences,

                    snapshot =
                        NavigationVoiceCatalogSnapshot(
                            supportedLanguageTags =
                                setOf(
                                    "de-DE"
                                ),

                            voices =
                                listOf(
                                    voice(
                                        id =
                                            "available"
                                    )
                                ),
                        ),
                )

        assertNull(
            sanitized.voiceId
        )
    }

    @Test
    fun unknownStoredLanguageFallsBackToProductDefaultForEditing() {
        val editable =
            NavigationVoiceCatalog
                .editablePreferences(
                    VoicePreferences(
                        languageTag =
                            "ja-JP",

                        voiceId =
                            "voice-ja",
                    )
                )

        assertEquals(
            "de-DE",
            editable.languageTag,
        )

        assertNull(
            editable.voiceId
        )
    }

    @Test
    fun profileIncompatibleVoiceIdsAreNotSelectable() {
        val tooLongId =
            "x".repeat(
                129
            )

        val snapshot =
            NavigationVoiceCatalogSnapshot(
                supportedLanguageTags =
                    setOf(
                        "de-DE"
                    ),

                voices =
                    listOf(
                        voice(
                            id =
                                tooLongId
                        )
                    ),
            )

        assertTrue(
            NavigationVoiceCatalog
                .voicesForLanguage(
                    snapshot =
                        snapshot,

                    languageTag =
                        "de-DE",
                )
                .isEmpty()
        )
    }

    @Test
    fun deviceVoiceLabelNeverInventsGenderMetadata() {
        val label =
            NavigationVoiceCatalog
                .deviceVoiceLabel(
                    voice(
                        id =
                            "vendor-voice"
                    )
                )
                .lowercase(
                    Locale.ROOT
                )

        assertFalse(
            label.contains(
                "female"
            )
        )

        assertFalse(
            label.contains(
                "male"
            )
        )

        assertFalse(
            label.contains(
                "weib"
            )
        )
    }

    private fun voice(
        id:
            String,

        languageTag:
            String =
            "de-DE",

        quality:
            Int =
            300,

        latency:
            Int =
            300,

        requiresNetwork:
            Boolean =
            false,
    ): NavigationDeviceVoice =
        NavigationDeviceVoice(
            id =
                id,

            languageTag =
                languageTag,

            quality =
                quality,

            latency =
                latency,

            requiresNetwork =
                requiresNetwork,

            features =
                emptySet(),
        )
}