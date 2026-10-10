package org.routingplatform.app.ai

import java.text.Normalizer

data class SocialAiPoiCategoryFeedback(
    val helpful: Int,
    val notHelpful: Int,
) {
    val score: Int
        get() = helpful - notHelpful

    val hasFeedback: Boolean
        get() = helpful + notHelpful > 0
}

/**
 * Profile-local, explicit feedback memory for active-route POI categories.
 *
 * Only category counters are persisted. Place names, coordinates, routes and
 * trip history are deliberately excluded.
 */
object SocialAiPoiPreferenceMemory {
    val categories: List<String> = listOf(
        "food",
        "fuel",
        "charging",
        "parking",
        "restroom",
        "pharmacy",
        "groceries",
        "lodging",
    )

    private const val KEY_PREFIX = "poi.category."
    private const val MAX_RATINGS_PER_KIND = 10_000

    fun categoryForQuery(query: String): String? {
        val text = normalize(query)
        if (text.isBlank()) return null

        return when {
            containsAny(text, "tankstelle", "tanken", "benz", "diesel", "fuel", "gas station") -> "fuel"
            containsAny(text, "ladestation", "e-lad", "elektroauto", "charger", "charging", "strom laden") -> "charging"
            containsAny(text, "parkplatz", "parkhaus", "parken", "parking") -> "parking"
            containsAny(text, "toilette", "wc", "restroom", "washroom") -> "restroom"
            containsAny(text, "apotheke", "pharmacy", "medikament") -> "pharmacy"
            containsAny(text, "supermarkt", "lebensmittel", "einkaufen", "grocery", "groceries") -> "groceries"
            containsAny(text, "hotel", "übernacht", "unterkunft", "lodging") -> "lodging"
            containsAny(text, "restaurant", "essen", "café", "cafe", "bäckerei", "imbiss", "food", "kaffee") -> "food"
            else -> null
        }
    }

    fun recall(
        repository: SocialAiLearningRepository,
        storeId: String,
    ): Map<String, SocialAiPoiCategoryFeedback> =
        repository.recall(storeId, maximumEntries = 64)
            .asSequence()
            .filter { it.kind == SocialAiKnowledgeKind.Preference && it.key.startsWith(KEY_PREFIX) }
            .mapNotNull { knowledge ->
                val category = knowledge.key.removePrefix(KEY_PREFIX)
                if (category !in categories) return@mapNotNull null
                val counts = parseCounts(knowledge.value) ?: return@mapNotNull null
                category to counts
            }
            .toMap()

    fun recordFeedback(
        repository: SocialAiLearningRepository,
        storeId: String,
        category: String,
        helpful: Boolean,
        nowEpochMillis: Long,
    ): SocialAiPoiCategoryFeedback {
        require(category in categories) { "Unsupported POI category." }
        require(nowEpochMillis >= 0L) { "Timestamp must be non-negative." }

        val current = recall(repository, storeId)[category]
            ?: SocialAiPoiCategoryFeedback(helpful = 0, notHelpful = 0)
        val next = if (helpful) {
            current.copy(helpful = (current.helpful + 1).coerceAtMost(MAX_RATINGS_PER_KIND))
        } else {
            current.copy(notHelpful = (current.notHelpful + 1).coerceAtMost(MAX_RATINGS_PER_KIND))
        }

        repository.rememberExplicitly(
            storeId = storeId,
            id = "poi-$category",
            kind = SocialAiKnowledgeKind.Preference,
            key = "$KEY_PREFIX$category",
            value = "${next.helpful},${next.notHelpful}",
            scope = SocialAiMemoryScope.LongTerm,
            source = "active-route-poi-feedback",
            observedAtEpochMillis = nowEpochMillis,
        )
        return next
    }

    private fun parseCounts(value: String): SocialAiPoiCategoryFeedback? {
        val parts = value.split(',')
        if (parts.size != 2) return null
        val helpful = parts[0].toIntOrNull()?.takeIf { it in 0..MAX_RATINGS_PER_KIND } ?: return null
        val notHelpful = parts[1].toIntOrNull()?.takeIf { it in 0..MAX_RATINGS_PER_KIND } ?: return null
        return SocialAiPoiCategoryFeedback(helpful, notHelpful)
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any { normalize(it) in text }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
}
