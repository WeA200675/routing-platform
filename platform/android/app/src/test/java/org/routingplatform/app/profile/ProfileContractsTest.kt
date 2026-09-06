package org.routingplatform.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileContractsTest {

    @Test
    fun guestProfileIsNonLearningAndNonPersonalized() {
        val profile =
            ProfileDefaults
                .guest()

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            profile.schemaVersion,
        )

        assertEquals(
            "guest",
            profile.profileId,
        )

        assertEquals(
            "Gast",
            profile.displayName,
        )

        assertFalse(
            profile.ai
                .personalizationEnabled
        )

        assertFalse(
            profile.ai
                .learningEnabled
        )

        assertFalse(
            profile.ai
                .useTripHistoryForPersonalization
        )
    }

    @Test
    fun namedProfileHasConservativeDefaults() {
        val profile =
            ProfileDefaults
                .named(
                    profileId =
                        "andreas",

                    displayName =
                        "Andreas",
                )

        assertEquals(
            DrivingStylePreference.Balanced,
            profile.driving.style,
        )

        assertEquals(
            RouteStylePreference.Balanced,
            profile.driving.routeStyle,
        )

        assertEquals(
            RouteStabilityPreference.Balanced,
            profile.driving.routeStability,
        )

        assertEquals(
            ProfileMapOrientation.HeadingUp,
            profile.display.mapOrientation,
        )

        assertEquals(
            ProfileAppearance.System,
            profile.display.appearance,
        )

        assertEquals(
            "de-DE",
            profile.voice.languageTag,
        )

        assertTrue(
            profile.ai
                .personalizationEnabled
        )

        assertFalse(
            profile.ai
                .learningEnabled
        )
    }

    @Test
    fun activeSessionKeepsExplicitAndLearnedStateSeparate() {
        val profile =
            UserProfile(
                profileId =
                    "driver-1",

                displayName =
                    "Fahrer 1",

                ai =
                    AiPreferences(
                        personalizationEnabled =
                            true,

                        learningEnabled =
                            true,

                        useTripHistoryForPersonalization =
                            false,
                    ),

                dataReferences =
                    ProfileDataReferences(
                        learnedPreferencesStoreId =
                            "learned-driver-1",

                        tripHistoryStoreId =
                            "trips-driver-1",

                        aiContextStoreId =
                            "ai-driver-1",
                    ),
            )

        val session =
            ProfileDefaults
                .session(
                    profile
                )

        assertEquals(
            "driver-1",
            session.profileId,
        )

        assertEquals(
            "Fahrer 1",
            session.displayName,
        )

        assertTrue(
            session.explicitPersonalizationEnabled
        )

        assertTrue(
            session.learningEnabled
        )

        assertFalse(
            profile.ai
                .useTripHistoryForPersonalization
        )

        assertEquals(
            "learned-driver-1",
            profile.dataReferences
                .learnedPreferencesStoreId,
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun blankDisplayNameIsRejected() {
        UserProfile(
            profileId =
                "valid-id",

            displayName =
                " ",
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun unsafeProfileIdIsRejected() {
        UserProfile(
            profileId =
                "../profile",

            displayName =
                "Profile",
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun invalidMapTiltIsRejected() {
        DisplayPreferences(
            mapTiltDegrees =
                80.0
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun invalidSpeechRateIsRejected() {
        VoicePreferences(
            speechRate =
                4.0
        )
    }
}