package org.routingplatform.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAiPoiPreferenceMemoryTest {
    @Test
    fun explicitRatingsPersistOnlyCategoryCountersAndInfluenceScore() {
        val repository = SocialAiLearningRepository(InMemoryPersistence())

        assertEquals(
            SocialAiPoiCategoryFeedback(1, 0),
            SocialAiPoiPreferenceMemory.recordFeedback(
                repository, "route-profile", "food", helpful = true, nowEpochMillis = 1L,
            ),
        )
        assertEquals(
            SocialAiPoiCategoryFeedback(1, 1),
            SocialAiPoiPreferenceMemory.recordFeedback(
                repository, "route-profile", "food", helpful = false, nowEpochMillis = 2L,
            ),
        )

        val remembered = SocialAiPoiPreferenceMemory.recall(repository, "route-profile").getValue("food")
        assertEquals(1, remembered.helpful)
        assertEquals(1, remembered.notHelpful)
        assertEquals(0, remembered.score)

        val stored = repository.recall("route-profile", maximumEntries = 64).single()
        assertEquals("poi.category.food", stored.key)
        assertEquals("1,1", stored.value)
        assertTrue(stored.userLocked)
        assertEquals("active-route-poi-feedback", stored.source)
    }

    @Test
    fun feedbackIsIsolatedPerProfileAndCategory() {
        val repository = SocialAiLearningRepository(InMemoryPersistence())
        SocialAiPoiPreferenceMemory.recordFeedback(
            repository, "route-profile-one", "fuel", helpful = true, nowEpochMillis = 5L,
        )

        assertEquals(1, SocialAiPoiPreferenceMemory.recall(repository, "route-profile-one").getValue("fuel").score)
        assertTrue(SocialAiPoiPreferenceMemory.recall(repository, "route-profile-two").isEmpty())
        assertNull(SocialAiPoiPreferenceMemory.recall(repository, "route-profile-one")["food"])
    }

    @Test
    fun queryCategoriesAreRecognizedWithoutKeepingPlaceNames() {
        assertEquals("fuel", SocialAiPoiPreferenceMemory.categoryForQuery("Ich muss kurz tanken"))
        assertEquals("charging", SocialAiPoiPreferenceMemory.categoryForQuery("E-Ladestation"))
        assertEquals("food", SocialAiPoiPreferenceMemory.categoryForQuery("Bäckerei"))
        assertEquals("restroom", SocialAiPoiPreferenceMemory.categoryForQuery("WC"))
        assertNull(SocialAiPoiPreferenceMemory.categoryForQuery("Café am Marktplatz 12"))
    }

    private class InMemoryPersistence : SocialAiMemoryPersistence {
        private val values = mutableMapOf<String, List<SocialAiKnowledge>>()

        override fun load(storeId: String): List<SocialAiKnowledge> = values[storeId].orEmpty()

        override fun save(storeId: String, knowledge: List<SocialAiKnowledge>): Boolean {
            values[storeId] = knowledge
            return true
        }

        override fun clear(storeId: String): Boolean = values.remove(storeId) != null

        override fun exists(storeId: String): Boolean = storeId in values
    }
}
