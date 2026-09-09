package org.routingplatform.app.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperiencePackRuntimeTest {

    @Test
    fun defaultPersonalityPreservesExistingPresentation() {
        val display =
            customDisplayPreferences()

        val voice =
            customVoicePreferences()

        val personality =
            NavigationPersonalityPreferences()

        assertEquals(
            display,
            ExperiencePackRuntimeResolver
                .resolveDisplayPreferences(
                    base =
                        display,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )

        assertEquals(
            voice,
            ExperiencePackRuntimeResolver
                .resolveVoicePreferences(
                    base =
                        voice,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )
    }

    @Test
    fun explicitPackOverridesOnlyAllowedPresentationFields() {
        val display =
            customDisplayPreferences()

        val voice =
            customVoicePreferences()

        val personality =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .ZEN_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Explicit,
            )

        val resolvedDisplay =
            ExperiencePackRuntimeResolver
                .resolveDisplayPreferences(
                    base =
                        display,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                )

        val resolvedVoice =
            ExperiencePackRuntimeResolver
                .resolveVoicePreferences(
                    base =
                        voice,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                )

        assertEquals(
            display.copy(
                mapStyle =
                    ProfileMapStyle.Minimal
            ),
            resolvedDisplay,
        )

        assertEquals(
            voice.copy(
                verbosity =
                    VoiceGuidanceVerbosity.Minimal
            ),
            resolvedVoice,
        )
    }

    @Test
    fun weeklyDiscoveryUsesDeterministicPackForBothOutputs() {
        val display =
            customDisplayPreferences()

        val voice =
            customVoicePreferences()

        val personality =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .CLASSIC_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Default,

                weeklyDiscoveryEnabled =
                    true,

                weeklyDiscoveryIntensity =
                    WeeklyDiscoveryIntensity.Creative,
            )

        val expectedPack =
            WeeklyExperiencePackSelector
                .select(
                    weekKey =
                        "2026-W37",

                    currentPackId =
                        ExperiencePackCatalog
                            .CLASSIC_PACK_ID,

                    intensity =
                        WeeklyDiscoveryIntensity.Creative,
                )

        assertEquals(
            display.copy(
                mapStyle =
                    expectedPack.mapStyle
            ),
            ExperiencePackRuntimeResolver
                .resolveDisplayPreferences(
                    base =
                        display,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )

        assertEquals(
            voice.copy(
                verbosity =
                    expectedPack.voiceVerbosity
            ),
            ExperiencePackRuntimeResolver
                .resolveVoicePreferences(
                    base =
                        voice,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )
    }

    @Test
    fun explicitSelectionWinsEvenWhenWeeklyFlagIsPresent() {
        val personality =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .GALACTIC_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Explicit,

                weeklyDiscoveryEnabled =
                    true,

                weeklyDiscoveryIntensity =
                    WeeklyDiscoveryIntensity.Wild,
            )

        val pack =
            ExperiencePackRuntimeResolver
                .resolvePackOverride(
                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                )

        assertEquals(
            ExperiencePackCatalog
                .GALACTIC_PACK_ID,
            pack?.packId,
        )
    }

    @Test
    fun disabledWeeklySourceFailsClosedToExistingPresentation() {
        val display =
            customDisplayPreferences()

        val voice =
            customVoicePreferences()

        val personality =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .GALACTIC_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.WeeklyDiscovery,

                weeklyDiscoveryEnabled =
                    false,
            )

        assertEquals(
            display,
            ExperiencePackRuntimeResolver
                .resolveDisplayPreferences(
                    base =
                        display,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )

        assertEquals(
            voice,
            ExperiencePackRuntimeResolver
                .resolveVoicePreferences(
                    base =
                        voice,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                ),
        )
    }

    @Test
    fun runtimeWeekKeyUsesIsoStyleUtcWeek() {
        assertEquals(
            "2026-W37",
            ExperiencePackRuntimeResolver
                .currentWeekKey(
                    nowEpochMillis =
                        1788955200000L
                ),
        )
    }

    private fun customDisplayPreferences():
        DisplayPreferences =
        DisplayPreferences(
            appearance =
                ProfileAppearance.Dark,

            mapStyle =
                ProfileMapStyle.HighContrast,

            mapOrientation =
                ProfileMapOrientation.NorthUp,

            mapTiltDegrees =
                12.0,

            defaultZoom =
                14.0,

            informationDensity =
                InformationDensityPreference.Detailed,

            textScale =
                1.2,

            routeLineScale =
                1.4,

            navigationControlSide =
                NavigationControlSide.Left,
        )

    private fun customVoicePreferences():
        VoicePreferences =
        VoicePreferences(
            enabled =
                false,

            languageTag =
                "de-DE",

            voiceId =
                "local.voice",

            speechRate =
                1.15,

            verbosity =
                VoiceGuidanceVerbosity.Detailed,

            preferredSpokenName =
                "Alex",
        )
}