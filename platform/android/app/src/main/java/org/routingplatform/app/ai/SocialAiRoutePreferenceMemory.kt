package org.routingplatform.app.ai

import org.routingplatform.app.navigation.NavigationRouteFamily
import java.security.MessageDigest

/** Profile-scoped route-family memory. It stores no destinations or trip history. */
internal object SocialAiRoutePreferenceMemory {
    private const val ENTRY_ID = "route-family"
    private const val MEMORY_KEY = "routing.family"

    fun storeId(profileId: String): String {
        require(profileId.isNotBlank())
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(profileId.toByteArray(Charsets.UTF_8))
            .take(24)
            .joinToString("") { "%02x".format(it) }
        return "route-$digest"
    }

    fun recall(repository: SocialAiLearningRepository, storeId: String): NavigationRouteFamily? =
        repository.recall(storeId, maximumEntries = 64)
            .firstOrNull { it.kind == SocialAiKnowledgeKind.Preference && it.key == MEMORY_KEY }
            ?.value
            ?.let { value -> runCatching { NavigationRouteFamily.valueOf(value) }.getOrNull() }

    fun rememberPositiveRating(
        repository: SocialAiLearningRepository,
        storeId: String,
        family: NavigationRouteFamily,
        learningEnabled: Boolean,
        nowEpochMillis: Long,
    ): Boolean {
        require(nowEpochMillis >= 0L)
        if (!learningEnabled) return false
        repository.rememberExplicitly(
            storeId = storeId,
            id = ENTRY_ID,
            kind = SocialAiKnowledgeKind.Preference,
            key = MEMORY_KEY,
            value = family.name,
            scope = SocialAiMemoryScope.LongTerm,
            source = "route-preview-rating",
            observedAtEpochMillis = nowEpochMillis,
        )
        return true
    }

    fun forget(repository: SocialAiLearningRepository, storeId: String): Boolean =
        repository.forget(storeId, ENTRY_ID)
}