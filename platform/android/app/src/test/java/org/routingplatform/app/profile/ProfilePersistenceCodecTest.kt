package org.routingplatform.app.profile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
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
                            accentColor =
                                ProfileAccentColor.Teal,
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

                    personality =
                        NavigationPersonalityPreferences(
                            selectedPackId =
                                ExperiencePackCatalog
                                    .GALACTIC_PACK_ID,

                            selectionSource =
                                ExperiencePackSelectionSource
                                    .Explicit,

                            weeklyDiscoveryEnabled =
                                true,

                            weeklyDiscoveryIntensity =
                                WeeklyDiscoveryIntensity
                                    .Wild,
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
    fun legacyV1PayloadMigratesToCurrentSchemaAndPersonalityDefaults() {
        val decoded =
            ProfilePersistenceCodec
                .decode(
                    legacyV1ProfilePayload()
                )

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            decoded.schemaVersion,
        )

        assertEquals(
            "legacy-driver",
            decoded.profileId,
        )
        assertEquals(
            ProfileAccentColor.Standard,
            decoded.display
                .accentColor,
        )

        assertEquals(
            ExperiencePackCatalog
                .CLASSIC_PACK_ID,
            decoded.personality
                .selectedPackId,
        )

        assertEquals(
            ExperiencePackSelectionSource
                .Default,
            decoded.personality
                .selectionSource,
        )

        assertEquals(
            false,
            decoded.personality
                .weeklyDiscoveryEnabled,
        )

        assertEquals(
            WeeklyDiscoveryIntensity
                .Creative,
            decoded.personality
                .weeklyDiscoveryIntensity,
        )
    }

    @Test
    fun legacyV3PayloadMigratesAccentColorToStandard() {
        val decoded =
            ProfilePersistenceCodec
                .decode(
                    legacyV3ProfilePayload()
                )

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            decoded.schemaVersion,
        )

        assertEquals(
            "legacy-v3-driver",
            decoded.profileId,
        )

        assertEquals(
            ProfileAccentColor.Standard,
            decoded.display
                .accentColor,
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

    private fun legacyV3ProfilePayload():
        String {

        val buffer =
            ByteArrayOutputStream()

        DataOutputStream(
            buffer
        ).use {
                output ->

            output.writeInt(
                0x52504631
            )

            output.writeInt(
                3
            )

            output.writeInt(
                3
            )

            output.writeUTF(
                "legacy-v3-driver"
            )

            output.writeUTF(
                "Legacy V3 Driver"
            )

            output.writeBoolean(
                true
            )

            output.writeUTF(
                "de-DE"
            )

            output.writeBoolean(
                false
            )

            output.writeDouble(
                1.0
            )

            output.writeUTF(
                VoiceGuidanceVerbosity
                    .Standard
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                DrivingStylePreference
                    .Balanced
                    .name
            )

            output.writeUTF(
                RouteStylePreference
                    .Balanced
                    .name
            )

            output.writeUTF(
                RouteStabilityPreference
                    .Balanced
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                InstructionLeadTimePreference
                    .Standard
                    .name
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeUTF(
                NavigationHapticIntensity
                    .Standard
                    .name
            )

            output.writeUTF(
                ProfileAppearance
                    .System
                    .name
            )

            output.writeUTF(
                ProfileMapStyle
                    .Standard
                    .name
            )

            output.writeUTF(
                ProfileMapOrientation
                    .HeadingUp
                    .name
            )

            output.writeDouble(
                45.0
            )

            output.writeDouble(
                16.0
            )

            output.writeUTF(
                InformationDensityPreference
                    .Standard
                    .name
            )

            output.writeDouble(
                1.0
            )

            output.writeDouble(
                1.0
            )

            output.writeUTF(
                NavigationControlSide
                    .Right
                    .name
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                AssistantStylePreference
                    .Standard
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                ExperiencePackCatalog
                    .CLASSIC_PACK_ID
            )

            output.writeUTF(
                ExperiencePackSelectionSource
                    .Default
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                WeeklyDiscoveryIntensity
                    .Creative
                    .name
            )
        }

        return Base64
            .getEncoder()
            .encodeToString(
                buffer.toByteArray()
            )
    }

    private fun legacyV1ProfilePayload():
        String {

        val buffer =
            ByteArrayOutputStream()

        DataOutputStream(
            buffer
        ).use {
                output ->

            output.writeInt(
                0x52504631
            )

            output.writeInt(
                1
            )

            output.writeInt(
                1
            )

            output.writeUTF(
                "legacy-driver"
            )

            output.writeUTF(
                "Legacy Driver"
            )

            output.writeBoolean(
                true
            )

            output.writeUTF(
                "de-DE"
            )

            output.writeBoolean(
                false
            )

            output.writeDouble(
                1.0
            )

            output.writeUTF(
                VoiceGuidanceVerbosity
                    .Standard
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                DrivingStylePreference
                    .Balanced
                    .name
            )

            output.writeUTF(
                RouteStylePreference
                    .Balanced
                    .name
            )

            output.writeUTF(
                RouteStabilityPreference
                    .Balanced
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                InstructionLeadTimePreference
                    .Standard
                    .name
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                true
            )

            output.writeUTF(
                ProfileAppearance
                    .System
                    .name
            )

            output.writeUTF(
                ProfileMapStyle
                    .Standard
                    .name
            )

            output.writeUTF(
                ProfileMapOrientation
                    .HeadingUp
                    .name
            )

            output.writeDouble(
                45.0
            )

            output.writeDouble(
                16.0
            )

            output.writeUTF(
                InformationDensityPreference
                    .Standard
                    .name
            )

            output.writeDouble(
                1.0
            )

            output.writeDouble(
                1.0
            )

            output.writeUTF(
                NavigationControlSide
                    .Right
                    .name
            )

            output.writeBoolean(
                true
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeUTF(
                AssistantStylePreference
                    .Standard
                    .name
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )

            output.writeBoolean(
                false
            )
        }

        return Base64
            .getEncoder()
            .encodeToString(
                buffer.toByteArray()
            )
    }
}