package org.routingplatform.app.ai

/**
 * Static architecture guard for the local inference package.
 *
 * The app may use networking for maps/routing, but local inference source files
 * must never acquire a transport dependency or a cloud fallback.
 */
object SocialAiOfflineArchitecturePolicy {
    val forbiddenSourceTokens: Set<String> = setOf(
        "java.net.",
        "javax.net.",
        "okhttp3.",
        "retrofit2.",
        "android.webkit.",
        "HttpURLConnection",
        "Socket(",
        "URL(",
    )

    fun violations(source: String): List<String> =
        forbiddenSourceTokens.filter(source::contains)
}
