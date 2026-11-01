package org.routingplatform.app.ai

import android.content.Context

enum class SocialAiOperatingMode { Development, Release }

enum class SocialAiOperation {
    ConversationInference,
    RoutingInterpretation,
    Training,
    ProposalGeneration,
    ProductionPromotion,
}

data class SocialAiControlState(
    val mode: SocialAiOperatingMode,
    val stopped: Boolean,
    val stopReason: String? = null,
    val changedAtEpochMillis: Long = 0L,
)

class SocialAiStoppedException(message: String) : IllegalStateException(message)

/** Small, synchronous policy boundary used by every AI entry point. */
class SocialAiDevelopmentControl(
    initialState: SocialAiControlState,
    private val persist: (SocialAiControlState) -> Unit = {},
) {
    @Volatile private var current = initialState

    fun state(): SocialAiControlState = current

    @Synchronized
    fun stop(reason: String, nowEpochMillis: Long): SocialAiControlState {
        val normalized = reason.trim().take(240)
        require(normalized.isNotEmpty()) { "Ein Stop-Grund ist erforderlich." }
        return update(current.copy(stopped = true, stopReason = normalized, changedAtEpochMillis = nowEpochMillis))
    }

    @Synchronized
    fun resume(nowEpochMillis: Long): SocialAiControlState =
        update(current.copy(stopped = false, stopReason = null, changedAtEpochMillis = nowEpochMillis))

    fun requireAllowed(operation: SocialAiOperation) {
        val snapshot = current
        if (snapshot.stopped) {
            throw SocialAiStoppedException("KI gestoppt: ${snapshot.stopReason ?: "manuell angefordert"}")
        }
        if (snapshot.mode == SocialAiOperatingMode.Release &&
            operation in setOf(SocialAiOperation.Training, SocialAiOperation.ProposalGeneration)
        ) {
            throw SocialAiStoppedException("Operation im Release-Modus nicht freigegeben: $operation")
        }
        if (operation == SocialAiOperation.ProductionPromotion && snapshot.mode != SocialAiOperatingMode.Release) {
            throw SocialAiStoppedException("Produktionsfreigabe ist im Entwicklungsmodus gesperrt.")
        }
    }

    private fun update(next: SocialAiControlState): SocialAiControlState {
        current = next
        persist(next)
        return next
    }
}

object SocialAiDevelopmentControlStore {
    private const val PREFS = "social_ai_development_control"
    private const val MODE = "mode"
    private const val STOPPED = "stopped"
    private const val REASON = "reason"
    private const val CHANGED_AT = "changed_at"

    fun from(context: Context): SocialAiDevelopmentControl {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = SocialAiControlState(
            mode = if (prefs.getString(MODE, null) == SocialAiOperatingMode.Release.name) SocialAiOperatingMode.Release else SocialAiOperatingMode.Development,
            stopped = prefs.getBoolean(STOPPED, false),
            stopReason = prefs.getString(REASON, null),
            changedAtEpochMillis = prefs.getLong(CHANGED_AT, 0L),
        )
        return SocialAiDevelopmentControl(state) { next ->
            // commit() makes the emergency stop durable before the UI returns.
            prefs.edit()
                .putString(MODE, next.mode.name)
                .putBoolean(STOPPED, next.stopped)
                .putString(REASON, next.stopReason)
                .putLong(CHANGED_AT, next.changedAtEpochMillis)
                .commit()
        }
    }
}
