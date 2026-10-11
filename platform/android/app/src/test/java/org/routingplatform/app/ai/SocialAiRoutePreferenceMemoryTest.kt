package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.NavigationRouteFamily

class SocialAiRoutePreferenceMemoryTest {
    @Test
    fun positiveRatingIsStoredOnlyWhenLearningIsEnabled() {
        val repository = SocialAiLearningRepository(InMemorySocialAiMemoryPersistence())
        val storeId = SocialAiRoutePreferenceMemory.storeId("profile-one")

        assertFalse(
            SocialAiRoutePreferenceMemory.rememberPositiveRating(
                repository, storeId, NavigationRouteFamily.Fastest,
                learningEnabled = false, nowEpochMillis = 100L,
            )
        )
        assertNull(SocialAiRoutePreferenceMemory.recall(repository, storeId))

        assertTrue(
            SocialAiRoutePreferenceMemory.rememberPositiveRating(
                repository, storeId, NavigationRouteFamily.Shortest,
                learningEnabled = true, nowEpochMillis = 200L,
            )
        )
        assertEquals(
            NavigationRouteFamily.Shortest,
            SocialAiRoutePreferenceMemory.recall(repository, storeId),
        )
        assertTrue(SocialAiRoutePreferenceMemory.forget(repository, storeId))
        assertNull(SocialAiRoutePreferenceMemory.recall(repository, storeId))
    }

    @Test
    fun profileStoresAreStableAndIsolated() {
        val first = SocialAiRoutePreferenceMemory.storeId("profile-one")
        assertEquals(first, SocialAiRoutePreferenceMemory.storeId("profile-one"))
        assertNotEquals(first, SocialAiRoutePreferenceMemory.storeId("profile-two"))
        assertTrue(first.matches(Regex("route-[a-f0-9]{48}")))
    }

    private class InMemorySocialAiMemoryPersistence : SocialAiMemoryPersistence {
        private val values = mutableMapOf<String, List<SocialAiKnowledge>>()

        override fun load(storeId: String): List<SocialAiKnowledge> =
            values[storeId].orEmpty()

        override fun save(storeId: String, knowledge: List<SocialAiKnowledge>): Boolean {
            values[storeId] = knowledge
            return true
        }

        override fun clear(storeId: String): Boolean = values.remove(storeId) != null

        override fun exists(storeId: String): Boolean = storeId in values
    }
}
