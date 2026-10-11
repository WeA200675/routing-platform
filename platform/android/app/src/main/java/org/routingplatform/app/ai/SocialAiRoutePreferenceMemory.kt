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
    ): Boolean =
        rememberFamily(
            repository = repository,
            storeId = storeId,
            family = family,
            learningEnabled = learningEnabled,
            nowEpochMillis = nowEpochMillis,
            source = "route-preview-rating",
        )

    fun rememberPostDriveRecommendation(
        repository: SocialAiLearningRepository,
        storeId: String,
        family: NavigationRouteFamily,
        learningEnabled: Boolean,
        nowEpochMillis: Long,
    ): Boolean =
        rememberFamily(
            repository = repository,
            storeId = storeId,
            family = family,
            learningEnabled = learningEnabled,
            nowEpochMillis = nowEpochMillis,
            source = "post-drive-route-feedback",
        )

    private fun rememberFamily(
        repository: SocialAiLearningRepository,
        storeId: String,
        family: NavigationRouteFamily,
        learningEnabled: Boolean,
        nowEpochMillis: Long,
        source: String,
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
            source = source,
            observedAtEpochMillis = nowEpochMillis,
        )
        return true
    }


    fun rememberPostDriveRating(
        repository: SocialAiLearningRepository,
        storeId: String,
        key: String,
        value: String,
        learningEnabled: Boolean,
        nowEpochMillis: Long,
    ): Boolean {
        require(nowEpochMillis >= 0L)
        if (!learningEnabled) return false
        repository.rememberExplicitly(
            storeId = storeId,
            id = "post-drive-" + key,
            kind = SocialAiKnowledgeKind.Preference,
            key = "post_drive." + key,
            value = value,
            scope = SocialAiMemoryScope.LongTerm,
            source = "post-drive-user-feedback",
            observedAtEpochMillis = nowEpochMillis,
        )
        return true
    }

    fun forget(repository: SocialAiLearningRepository, storeId: String): Boolean =
        repository.forget(storeId, ENTRY_ID)
}