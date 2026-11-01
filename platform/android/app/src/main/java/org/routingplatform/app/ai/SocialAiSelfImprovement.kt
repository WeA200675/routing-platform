package org.routingplatform.app.ai

import android.content.Context
import java.util.concurrent.atomic.AtomicLong

enum class SocialAiImprovementAction { Observe, ImproveSoon, RecoveryTrainingRequired }

data class SocialAiQualityState(
    val recentFailures: Int = 0,
    val totalFailures: Long = 0,
    val lastFailureAt: Long = 0,
    val lastFailureCategory: String? = null,
    val recoveryRequired: Boolean = false,
)

/** Failure-aware controller: improve before the hard limit, recover at the hard limit. */
class SocialAiSelfImprovementController(
    private val proactiveThreshold: Int = 2,
    private val recoveryThreshold: Int = 3,
    private val windowSize: Int = 5,
    initial: SocialAiQualityState = SocialAiQualityState(),
    private val persist: (SocialAiQualityState) -> Unit = {},
) {
    init { require(proactiveThreshold in 1 until recoveryThreshold && windowSize >= recoveryThreshold) }
    @Volatile private var state = initial

    @Synchronized
    fun recordSuccess(): SocialAiImprovementAction {
        state = state.copy(recentFailures = 0, recoveryRequired = false)
        persist(state)
        return SocialAiImprovementAction.Observe
    }

    @Synchronized
    fun recordFailure(category: String, nowEpochMillis: Long): SocialAiImprovementAction {
        val next = state.copy(
            recentFailures = (state.recentFailures + 1).coerceAtMost(windowSize),
            totalFailures = state.totalFailures + 1,
            lastFailureAt = nowEpochMillis,
            lastFailureCategory = category.trim().take(80).ifBlank { "unknown" },
            recoveryRequired = state.recentFailures + 1 >= recoveryThreshold,
        )
        state = next
        persist(next)
        return when {
            next.recoveryRequired -> SocialAiImprovementAction.RecoveryTrainingRequired
            next.recentFailures >= proactiveThreshold -> SocialAiImprovementAction.ImproveSoon
            else -> SocialAiImprovementAction.Observe
        }
    }

    fun snapshot(): SocialAiQualityState = state
}

object SocialAiSelfImprovementStore {
    private const val PREFS = "social_ai_self_improvement"

    fun from(context: Context): SocialAiSelfImprovementController {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val initial = SocialAiQualityState(
            recentFailures = prefs.getInt("recent_failures", 0),
            totalFailures = prefs.getLong("total_failures", 0L),
            lastFailureAt = prefs.getLong("last_failure_at", 0L),
            lastFailureCategory = prefs.getString("last_failure_category", null),
            recoveryRequired = prefs.getBoolean("recovery_required", false),
        )
        return SocialAiSelfImprovementController(initial = initial) { state ->
            prefs.edit()
                .putInt("recent_failures", state.recentFailures)
                .putLong("total_failures", state.totalFailures)
                .putLong("last_failure_at", state.lastFailureAt)
                .putString("last_failure_category", state.lastFailureCategory)
                .putBoolean("recovery_required", state.recoveryRequired)
                .commit()
        }
    }
}

/** Starts a bounded, offline recovery run exactly once when the quality gate trips.
 * No UI, approval or user interaction is required. Device health constraints remain active.
 */
object SocialAiRecoveryTraining {
    private val recoveryId = AtomicLong(0L)

    fun enqueueIfRequired(context: Context, action: SocialAiImprovementAction) {
        if (action != SocialAiImprovementAction.RecoveryTrainingRequired) return
        val existing = AiTrainingStore.currentSession(context)
        if (existing != null && existing.status in setOf("Läuft", "Wartet auf Bedingungen", "Pausiert")) return
        val track = AiTrainingPrioritizer.nextTrack(AiTrainingStore.results(context))
        val session = AiTrainingSession(
            id = "recovery-${System.currentTimeMillis()}-${recoveryId.incrementAndGet()}",
            trackId = track.id,
            total = 12,
            completed = 0,
            passed = 0,
            averageLatencyMs = 0L,
            status = "Wartet auf Bedingungen",
            requiresCharging = false,
            maxTokens = 128,
            customFocus = "Fehlerklasse gezielt wiederholen: ${track.title}",
            startedAt = System.currentTimeMillis(),
            message = "Autonomes Recovery-Training nach wiederholten KI-Fehlern; keine Benutzeraktion erforderlich.",
        )
        AiTrainingStore.begin(context, session)
        AiTrainingScheduler.enqueue(context, session, append = false)
    }
}
