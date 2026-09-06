package org.routingplatform.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProfilePersistenceCodecTest {

    @Test
    fun completeExplicitProfileRoundTrips() {
        val profile =
            ProfileDefaults
                .named(
                    profileId =
                        "driver-a",

                    displayName =
                        "Fahrer A",
                )
                .copy(
                    voice =
                        VoicePreferences(
                            enabled =
                                true,

                            languageTag =
                                "de-CH",

                            voiceId =
                                "voice-1",

                            speechRate =
                                1.15,

                            verbosity =
                                VoiceGuidanceVerbosity.Detailed,

                            preferredSpokenName =
                                "Alex",
                        ),

                    driving =
                        DrivingPreferences(
                            style =
                                DrivingStylePreference.Relaxed,

                            routeStyle =
                                RouteStylePreference.MajorRoads,

                            routeStability =
                                RouteStabilityPreference.Stable,

                            preferMajorRoads =
                                true,

                            avoidComplexTurns =
                                true,
                        ),

                    display =
                        DisplayPreferences(
                            appearance =
                                ProfileAppearance.Dark,

                            mapStyle =
                                ProfileMapStyle.Night,

                            mapOrientation =
                                ProfileMapOrientation.NorthUp,

                            mapTiltDegrees =
                                30.0,

                            defaultZoom =
                                17.0,

                            informationDensity =
                                InformationDensityPreference.Detailed,

                            textScale =
                                1.1,

                            routeLineScale =
                                1.25,

                            navigationControlSide =
                                NavigationControlSide.Left,
                        ),

                    ai =
                        AiPreferences(
                            personalizationEnabled =
                                true,

                            learningEnabled =
                                false,

                            useTripHistoryForPersonalization =
                                false,

                            assistantStyle =
                                AssistantStylePreference.Concise,
                        ),

                    dataReferences =
                        ProfileDataReferences(
                            learnedPreferencesStoreId =
                                "learned-driver-a",

                            tripHistoryStoreId =
                                "trips-driver-a",

                            aiContextStoreId =
                                "ai-driver-a",
                        ),
                )

        val decoded =
            ProfilePersistenceCodec
                .decode(
                    ProfilePersistenceCodec
                        .encode(
                            profile
                        )
                )

        assertEquals(
            profile,
            decoded,
        )
    }

    @Test
    fun invalidPayloadFailsClosed() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            ProfilePersistenceCodec
                .decode(
                    "not-a-profile!"
                )
        }
    }
}