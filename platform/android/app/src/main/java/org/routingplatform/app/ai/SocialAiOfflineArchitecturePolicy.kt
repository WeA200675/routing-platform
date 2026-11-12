package org.routingplatform.app.ai

/**
 * Static architecture guard for the local inference package.
 *
 * The app may use networking for maps/routing, but local inference source files
 * must never acquire a transport dependency or a cloud fallback.
 */
object SocialAiOfflineArchitecturePolicy {
    private val forbiddenImportPrefixes = setOf(
        "java.net.",
        "javax.net.",
        "okhttp3.",
        "retrofit2.",
        "android.webkit.",
    )

    private val forbiddenQualifiedCalls = setOf(
        "java.net.URL(",
        "java.net.Socket(",
        "java.net.HttpURLConnection",
    )

    fun violations(source: String): List<String> {
        val imported = source.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("import ") }
            .map { it.removePrefix("import ").substringBefore(" as ") }
            .filter { importedName -> forbiddenImportPrefixes.any(importedName::startsWith) }
            .map { "forbidden import: $it" }

        val qualified = forbiddenQualifiedCalls.asSequence()
            .filter(source::contains)
            .map { "forbidden qualified call: $it" }

        return (imported + qualified).toList()
    }
}
