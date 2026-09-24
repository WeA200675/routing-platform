package org.routingplatform.app.ai

/**
 * Deterministic escape hatch for ordinary destination phrases when the local
 * model cannot produce the strict symbolic routing contract.
 *
 * This class never creates coordinates. It only extracts a bounded search
 * query which must still pass through the trusted destination search source.
 */
object DeterministicDestinationFallback {
    fun extractSearchQuery(rawCommand: String): String? {
        // Validate the original input before normalization. Otherwise trim(),
        // whitespace collapsing or truncation could hide control characters
        // or turn an oversized command into an accepted one.
        if (rawCommand.length !in MIN_COMMAND_CHARS..MAX_COMMAND_CHARS) return null
        if (rawCommand.any { it.isISOControl() }) return null

        val normalized = rawCommand.trim().replace(WHITESPACE, " ")
        if (normalized.length < MIN_QUERY_CHARS) return null

        val stripped = PREFIXES.firstNotNullOfOrNull { prefix ->
            prefix.find(normalized)?.groupValues?.getOrNull(1)?.trim()
        }
        val candidate = (stripped ?: normalized).trim().trimEnd('.', '!', '?').trim()
        if (candidate.length !in MIN_QUERY_CHARS..MAX_QUERY_CHARS) return null
        if (candidate.equals("home", true) || candidate.equals("work", true) ||
            candidate.equals("zuhause", true) || candidate.equals("arbeit", true)
        ) return null
        return candidate
    }

    private const val MIN_COMMAND_CHARS = 2
    private const val MAX_COMMAND_CHARS = 512
    private const val MIN_QUERY_CHARS = 2
    private const val MAX_QUERY_CHARS = 160
    private val WHITESPACE = Regex("""\s+""")
    private val PREFIXES = listOf(
        Regex("""(?i)^fahr(?:e)?\s+mich\s+nach\s+(.+)$"""),
        Regex("""(?i)^navigier(?:e)?\s+(?:mich\s+)?nach\s+(.+)$"""),
        Regex("""(?i)^route\s+nach\s+(.+)$"""),
        Regex("""(?i)^nach\s+(.+)$"""),
    )
}
