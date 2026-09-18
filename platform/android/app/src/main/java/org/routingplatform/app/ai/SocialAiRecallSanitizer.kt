package org.routingplatform.app.ai

/**
 * Produces bounded model-facing memory context without changing the persisted
 * knowledge. Control characters are removed to keep prompt framing stable.
 */
object SocialAiRecallSanitizer {
    fun sanitize(
        knowledge: List<SocialAiKnowledge>,
        maximumEntries: Int = 24,
        maximumValueCharacters: Int = 512,
    ): List<SocialAiKnowledge> {
        require(maximumEntries in 1..64)
        require(maximumValueCharacters in 1..2_048)

        return knowledge
            .take(maximumEntries)
            .map { entry ->
                entry.copy(
                    key =
                        clean(entry.key)
                            .take(160),
                    value =
                        clean(entry.value)
                            .take(maximumValueCharacters),
                    contextKey =
                        entry.contextKey
                            ?.let(::clean)
                            ?.take(160),
                    source =
                        clean(entry.source)
                            .take(160),
                )
            }
    }

    private fun clean(value: String): String =
        value.filter { character ->
            character == '\n' ||
                character == '\t' ||
                !character.isISOControl()
        }
}
