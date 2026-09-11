package org.routingplatform.app.profile

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class G610HapticProfilePersistenceTest {

    @Test
    fun currentProfileDefaultsEnableStandardHaptics() {
        val profile =
            ProfileDefaults.guest()

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            profile.schemaVersion,
        )

        assertEquals(
            true,
            profile.navigation
                .hapticGuidanceEnabled,
        )

        assertEquals(
            NavigationHapticIntensity.Standard,
            profile.navigation
                .hapticIntensity,
        )
    }

    @Test
    fun explicitHapticPreferencesRoundTrip() {
        val profile =
            ProfileDefaults
                .named(
                    profileId =
                        "haptic-driver",

                    displayName =
                        "Haptic Driver",
                )
                .copy(
                    navigation =
                        NavigationPreferences(
                            instructionLeadTime =
                                InstructionLeadTimePreference
                                    .Early,

                            hapticGuidanceEnabled =
                                false,

                            hapticIntensity =
                                NavigationHapticIntensity
                                    .Strong,
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
    fun legacyV2ProfileMigratesWithHapticDefaults() {
        val decoded =
            ProfilePersistenceCodec
                .decode(
                    legacyV2ProfilePayload()
                )

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            decoded.schemaVersion,
        )

        assertEquals(
            "legacy-v2-driver",
            decoded.profileId,
        )

        assertEquals(
            true,
            decoded.navigation
                .hapticGuidanceEnabled,
        )

        assertEquals(
            NavigationHapticIntensity.Standard,
            decoded.navigation
                .hapticIntensity,
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
    }

    private fun legacyV2ProfilePayload():
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
                2
            )

            output.writeInt(
                2
            )

            output.writeUTF(
                "legacy-v2-driver"
            )

            output.writeUTF(
                "Legacy V2 Driver"
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
}