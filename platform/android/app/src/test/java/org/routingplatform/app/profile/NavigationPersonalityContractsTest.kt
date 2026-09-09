package org.routingplatform.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPersonalityContractsTest {

    @Test
    fun builtInCatalogHasUniqueStableIds() {
        val packs =
            ExperiencePackCatalog
                .builtIns

        assertEquals(
            3,
            packs.size,
        )

        assertEquals(
            packs.size,
            packs
                .map {
                    it.packId
                }
                .toSet()
                .size,
        )

        assertTrue(
            packs.all {
                it.version >=
                    1
            }
        )
    }

    @Test
    fun builtInPacksExposeDistinctProductCharacters() {
        val classic =
            ExperiencePackCatalog
                .require(
                    ExperiencePackCatalog
                        .CLASSIC_PACK_ID
                )

        val zen =
            ExperiencePackCatalog
                .require(
                    ExperiencePackCatalog
                        .ZEN_PACK_ID
                )

        val galactic =
            ExperiencePackCatalog
                .require(
                    ExperiencePackCatalog
                        .GALACTIC_PACK_ID
                )

        assertEquals(
            ProfileMapStyle.Standard,
            classic.mapStyle,
        )

        assertEquals(
            VoiceGuidanceVerbosity.Minimal,
            zen.voiceVerbosity,
        )

        assertEquals(
            NavigationPersonalityTone.Futuristic,
            galactic.tone,
        )

        assertNotEquals(
            classic.genre,
            galactic.genre,
        )
    }

    @Test
    fun explicitPackSelectionAlwaysWinsOverWeeklySuggestion() {
        val preferences =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .ZEN_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Explicit,

                weeklyDiscoveryEnabled =
                    true,

                weeklyDiscoveryIntensity =
                    WeeklyDiscoveryIntensity.Wild,
            )

        val resolved =
            ExperiencePackSelectionPolicy
                .resolve(
                    preferences =
                        preferences,

                    weeklySuggestionPackId =
                        ExperiencePackCatalog
                            .GALACTIC_PACK_ID,
                )

        assertEquals(
            ExperiencePackCatalog
                .ZEN_PACK_ID,
            resolved.packId,
        )
    }

    @Test
    fun weeklySuggestionCanApplyWhenUserHasNotPinnedAPack() {
        val preferences =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .CLASSIC_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Default,

                weeklyDiscoveryEnabled =
                    true,
            )

        val resolved =
            ExperiencePackSelectionPolicy
                .resolve(
                    preferences =
                        preferences,

                    weeklySuggestionPackId =
                        ExperiencePackCatalog
                            .GALACTIC_PACK_ID,
                )

        assertEquals(
            ExperiencePackCatalog
                .GALACTIC_PACK_ID,
            resolved.packId,
        )
    }

    @Test
    fun weeklySelectorIsDeterministicAndHonorsRejections() {
        val first =
            WeeklyExperiencePackSelector
                .select(
                    weekKey =
                        "2026-W37",

                    currentPackId =
                        ExperiencePackCatalog
                            .CLASSIC_PACK_ID,

                    intensity =
                        WeeklyDiscoveryIntensity.Wild,

                    rejectedPackIds =
                        setOf(
                            ExperiencePackCatalog
                                .ZEN_PACK_ID
                        ),
                )

        val second =
            WeeklyExperiencePackSelector
                .select(
                    weekKey =
                        "2026-W37",

                    currentPackId =
                        ExperiencePackCatalog
                            .CLASSIC_PACK_ID,

                    intensity =
                        WeeklyDiscoveryIntensity.Wild,

                    rejectedPackIds =
                        setOf(
                            ExperiencePackCatalog
                                .ZEN_PACK_ID
                        ),
                )

        assertEquals(
            first,
            second,
        )

        assertEquals(
            ExperiencePackCatalog
                .GALACTIC_PACK_ID,
            first.packId,
        )
    }

    @Test
    fun subtleDiscoveryNeverUsesHighSurprisePack() {
        val selected =
            WeeklyExperiencePackSelector
                .select(
                    weekKey =
                        "2026-W38",

                    currentPackId =
                        ExperiencePackCatalog
                            .CLASSIC_PACK_ID,

                    intensity =
                        WeeklyDiscoveryIntensity.Subtle,
                )

        assertTrue(
            selected.surpriseLevel <=
                WeeklyDiscoveryIntensity
                    .Subtle
                    .maximumSurpriseLevel
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun invalidSelectedPackIdFailsClosed() {
        NavigationPersonalityPreferences(
            selectedPackId =
                "NOT VALID"
        )
    }
}