package org.routingplatform.app.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiProfilePersistenceTest {

    @Test
    fun socialAiSettingsRoundTripWithSchemaFive() {
        val source =
            UserProfile(
                profileId = "social-ai",
                displayName = "Social AI",
                ai =
                    AiPreferences(
                        personalizationEnabled = true,
                        learningEnabled = true,
                        useTripHistoryForPersonalization = true,
                        assistantStyle =
                            AssistantStylePreference.Detailed,
                        socialAdaptationEnabled = true,
                        humorLevel = 100,
                        charmLevel = 82,
                        playfulnessLevel = 91,
                        proactivityLevel = 64,
                        flirtLevel = 73,
                        adultFlirtOptIn = true,
                    ),
                dataReferences =
                    ProfileDataReferences(
                        learnedPreferencesStoreId =
                            "social-ai-memory",
                        aiContextStoreId =
                            "social-ai-context",
                    ),
            )

        val decoded =
            ProfilePersistenceCodec.decode(
                ProfilePersistenceCodec.encode(source)
            )

        assertEquals(
            USER_PROFILE_SCHEMA_VERSION,
            decoded.schemaVersion,
        )
        assertEquals(source.ai, decoded.ai)
        assertEquals(source.dataReferences, decoded.dataReferences)
        assertEquals(73, decoded.ai.effectiveFlirtLevel)
    }

    @Test
    fun defaultProfileDoesNotEnableAdultFlirt() {
        val defaults =
            AiPreferences()

        assertFalse(defaults.adultFlirtOptIn)
        assertEquals(0, defaults.flirtLevel)
        assertEquals(0, defaults.effectiveFlirtLevel)
        assertTrue(defaults.socialAdaptationEnabled)
    }

    @Test
    fun flirtLevelIsSuppressedWithoutAdultOptIn() {
        val settings =
            AiPreferences(
                flirtLevel = 100,
                adultFlirtOptIn = false,
            )

        assertEquals(100, settings.flirtLevel)
        assertEquals(0, settings.effectiveFlirtLevel)
    }

    @Test
    fun socialLevelsAreStrictlyBounded() {
        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AiPreferences(
                humorLevel = 101
            )
        }

        assertThrows(
            IllegalArgumentException::class.java
        ) {
            AiPreferences(
                flirtLevel = -1
            )
        }
    }
}
