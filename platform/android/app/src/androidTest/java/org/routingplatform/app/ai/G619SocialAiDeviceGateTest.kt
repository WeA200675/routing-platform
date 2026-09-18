package org.routingplatform.app.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.routingplatform.app.profile.AiPreferences
import org.routingplatform.app.profile.AndroidUserProfileStore
import org.routingplatform.app.profile.ProfileDataReferences
import org.routingplatform.app.profile.UserProfile

@RunWith(AndroidJUnit4::class)
class G619SocialAiDeviceGateTest {
    private lateinit var context: Context

    @Before
    fun resetStores() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("routing-platform-user-profiles-v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("routing-platform-social-ai-memory-v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun profileV5PersistsAdultOptInAndEffectiveFlirt() {
        val store = AndroidUserProfileStore(context)
        val profile = UserProfile(
            profileId = "g619-device",
            displayName = "G6.19 Device",
            ai = AiPreferences(
                learningEnabled = true,
                humorLevel = 100,
                charmLevel = 80,
                playfulnessLevel = 90,
                proactivityLevel = 70,
                flirtLevel = 73,
                adultFlirtOptIn = true,
            ),
            dataReferences = ProfileDataReferences(
                learnedPreferencesStoreId = "g619-device-memory",
            ),
        )
        assertTrue(store.saveAndActivate(profile))
        val reloaded = AndroidUserProfileStore(context).loadActiveProfile()
        assertEquals(5, reloaded.schemaVersion)
        assertTrue(reloaded.ai.learningEnabled)
        assertTrue(reloaded.ai.adultFlirtOptIn)
        assertEquals(73, reloaded.ai.effectiveFlirtLevel)
        assertEquals(0, reloaded.ai.copy(adultFlirtOptIn = false).effectiveFlirtLevel)
    }

    @Test
    fun explicitMemoryPersistsRecallForgetsAndClears() {
        val storeId = "g619-device-memory"
        val persistence = AndroidSocialAiMemoryStore(context)
        val repository = SocialAiLearningRepository(persistence)
        repository.rememberExplicitly(
            storeId = storeId,
            id = "humor-pref",
            kind = SocialAiKnowledgeKind.SocialPreference,
            key = "humor",
            value = "mag trockenen Humor",
            observedAtEpochMillis = 1L,
        )
        val restartedRepository = SocialAiLearningRepository(AndroidSocialAiMemoryStore(context))
        val recalled = restartedRepository.recall(storeId)
        assertEquals(1, recalled.size)
        assertTrue(recalled.single().userLocked)
        assertEquals("mag trockenen Humor", recalled.single().value)
        assertTrue(restartedRepository.forget(storeId, "humor-pref"))
        assertTrue(restartedRepository.recall(storeId).isEmpty())

        repository.rememberExplicitly(
            storeId = storeId,
            id = "second-pref",
            kind = SocialAiKnowledgeKind.Preference,
            key = "voice",
            value = "kurz",
            observedAtEpochMillis = 2L,
        )
        assertTrue(restartedRepository.forgetAll(storeId))
        assertFalse(persistence.exists(storeId))
    }

    @Test
    fun criticalGuidanceSuppressesNonessentialSocialOnDevice() {
        val plan = SocialAiRuntime.plan(
            SocialAiRuntimeInput(
                settings = SocialAiPersonalitySettings(
                    humorLevel = 100,
                    charmLevel = 100,
                    playfulnessLevel = 100,
                    proactivityLevel = 100,
                    flirtLevel = 100,
                    adultFlirtOptIn = true,
                ),
                warmthLevel = 10,
                directnessLevel = 10,
                context = SocialAiResponseContext.CriticalGuidance,
            )
        )
        assertEquals(0, plan.humorLevel)
        assertEquals(0, plan.charmLevel)
        assertEquals(0, plan.playfulnessLevel)
        assertEquals(0, plan.proactivityLevel)
        assertEquals(0, plan.flirtLevel)
        assertFalse(plan.allowBanter)
        assertFalse(plan.allowFlirt)
        assertTrue(plan.suppressNonessentialSocial)
        assertTrue(plan.warmthLevel >= 70)
        assertTrue(plan.directnessLevel >= 90)
    }
}
