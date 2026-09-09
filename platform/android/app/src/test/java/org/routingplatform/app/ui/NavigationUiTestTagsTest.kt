package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationFaultCode
import org.routingplatform.app.navigation.NavigationRouteAcquisitionState
import org.routingplatform.app.navigation.NavigationSessionState

class NavigationUiTestTagsTest {

    @Test
    fun stableTerminalStateTagsDoNotDependOnLocalizedText() {
        assertEquals(
            "rp.navigation.session.Preview",
            NavigationUiTestTags
                .sessionState(
                    NavigationSessionState.Preview
                ),
        )

        assertEquals(
            "rp.navigation.acquisition.LiveFailed.NoSuitableEdges",
            NavigationUiTestTags
                .routeAcquisition(
                    state =
                        NavigationRouteAcquisitionState.LiveFailed,

                    faultCode =
                        NavigationFaultCode.NoSuitableEdges,
                ),
        )

        assertEquals(
            "rp.navigation.acquisition.LiveFailed.TransportUnavailable",
            NavigationUiTestTags
                .routeAcquisition(
                    state =
                        NavigationRouteAcquisitionState.LiveFailed,

                    faultCode =
                        NavigationFaultCode.TransportUnavailable,
                ),
        )
    }

    @Test
    fun staticTagsAreUniqueAndNamespaced() {
        val tags =
            listOf(
                NavigationUiTestTags.Root,
                NavigationUiTestTags.RouteId,
                NavigationUiTestTags.PrimaryAction,
                NavigationUiTestTags.PlannerOpen,
                NavigationUiTestTags.PlannerDialog,
                NavigationUiTestTags.SearchField,
                NavigationUiTestTags.SearchAction,
                NavigationUiTestTags.UseDestination,
                NavigationUiTestTags.AppendVia,
                NavigationUiTestTags.CustomFavoriteField,
                NavigationUiTestTags.CustomFavoriteSave,
                NavigationUiTestTags.ExperiencePackOpen,
                NavigationUiTestTags.ExperiencePackDialog,
                NavigationUiTestTags.WeeklyDiscoveryToggle,
                NavigationUiTestTags.VoiceSettingsOpen,
                NavigationUiTestTags.VoiceSettingsDialog,
                NavigationUiTestTags.VoicePreview,
                NavigationUiTestTags.VoiceSave,
            )

        assertEquals(
            tags.size,
            tags.toSet().size,
        )

        assertTrue(
            tags.all {
                it.startsWith(
                    "rp.navigation."
                )
            }
        )
    }

    @Test
    fun experiencePackTagsAreStableAndNamespaced() {
        assertEquals(
            "rp.navigation.experience_pack.galactic",
            NavigationUiTestTags
                .experiencePack(
                    "galactic"
                ),
        )

        assertEquals(
            "rp.navigation.weekly_intensity.Wild",
            NavigationUiTestTags
                .weeklyIntensity(
                    "Wild"
                ),
        )
    }

    @Test
    fun voiceLanguageTagsAreStableAndNamespaced() {
        assertEquals(
            "rp.navigation.voice_language.en-GB",
            NavigationUiTestTags
                .voiceLanguage(
                    "en-GB"
                ),
        )

        assertEquals(
            "rp.navigation.voice_language.de-DE",
            NavigationUiTestTags
                .voiceLanguage(
                    "de-DE"
                ),
        )
    }

    @Test
    fun dynamicSearchResultTagsAreStableAndDistinct() {
        assertEquals(
            "rp.navigation.search_result.0",
            NavigationUiTestTags
                .searchResult(
                    0
                ),
        )

        assertEquals(
            "rp.navigation.search_result.1",
            NavigationUiTestTags
                .searchResult(
                    1
                ),
        )

        assertTrue(
            NavigationUiTestTags
                .searchResult(
                    0
                ) !=
                NavigationUiTestTags
                    .searchResult(
                        1
                    )
        )
    }
}