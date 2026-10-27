package org.routingplatform.app.navigation

enum class NavigationResponseLengthTier(
    val minimumPositiveRuns: Int,
    val label: String,
    val maximumResponseTokens: Int,
) {
    Foundation(16, "Grundlage", 256),
    Compact(32, "Kompakt", 512),
    Standard(64, "Standard", 1024),
    Detailed(128, "Detailliert", 2048),
    Expert(256, "Expertenanalyse", 4096),
    Extended(512, "Erweitert", 8192),
    Full(1024, "Vollständige Analyse", 16384);

    companion object {
        fun fromPositiveRuns(runs: Int): NavigationResponseLengthTier =
            entries.lastOrNull {
                runs >= it.minimumPositiveRuns
            } ?: Foundation
    }
}
