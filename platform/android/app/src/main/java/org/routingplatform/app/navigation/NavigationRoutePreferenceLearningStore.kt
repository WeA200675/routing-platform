package org.routingplatform.app.navigation

import android.content.Context

/**
 * Explicit, on-device feedback for AI route comparison. This stores only a
 * route family and a bounded positive/negative count; no coordinates or trip
 * history are retained.
 */
object NavigationRoutePreferenceLearningStore {
    private const val PREFS = "local_route_preference_learning_v1"
    private const val MAX_FEEDBACK_PER_FAMILY = 100

    fun record(context: Context, family: NavigationRouteFamily, accepted: Boolean) {
        val key = key(family, accepted)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = (prefs.getInt(key, 0) + 1).coerceAtMost(MAX_FEEDBACK_PER_FAMILY)
        prefs.edit().putInt(key, next).apply()
    }

    fun preferredFamily(context: Context): Pair<NavigationRouteFamily, String>? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val evidence = NavigationRouteFamily.entries.mapNotNull { family ->
            val accepted = prefs.getInt(key(family, true), 0)
            val rejected = prefs.getInt(key(family, false), 0)
            if (accepted + rejected < 2) null else Triple(family, accepted, rejected)
        }
        val best = evidence.maxByOrNull { (_, accepted, rejected) ->
            (accepted + 1.0) / (accepted + rejected + 2.0)
        } ?: return null
        val (family, accepted, rejected) = best
        if (accepted <= rejected) return null
        return family to "$accepted positive, $rejected negative"
    }

    fun feedbackCounts(context: Context, family: NavigationRouteFamily): Pair<Int, Int> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getInt(key(family, true), 0) to prefs.getInt(key(family, false), 0)
    }

    private fun key(family: NavigationRouteFamily, accepted: Boolean) =
        "${family.name}_${if (accepted) "accepted" else "rejected"}"
}
