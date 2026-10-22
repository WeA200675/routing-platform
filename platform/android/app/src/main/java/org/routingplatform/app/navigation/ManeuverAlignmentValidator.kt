package org.routingplatform.app.navigation

/**
 * Read-only alignment evidence for the local AI diagnostics/training path.
 *
 * This validator never creates or changes a maneuver. It only compares the
 * maneuver identity used by the map and voice layers.
 */
internal data class ManeuverAlignmentObservation(
    val sessionId: String,
    val routeId: String,
    val displayed: ManeuverIdentity?,
    val spoken: ManeuverIdentity?,
    val displayedAtElapsedRealtimeMs: Long,
    val spokenAtElapsedRealtimeMs: Long?,
)

internal data class ManeuverIdentity(
    val maneuverType: ManeuverType,
    val instruction: String,
    val beginShapeIndex: Int?,
    val endShapeIndex: Int?,
)

internal enum class ManeuverAlignmentStatus {
    NotComparable,
    Aligned,
    SequenceMismatch,
    ContentMismatch,
    StaleSpeech,
}

internal data class ManeuverAlignmentResult(
    val status: ManeuverAlignmentStatus,
    val reason: String,
) {
    val isSafeForLearning: Boolean
        get() = status != ManeuverAlignmentStatus.NotComparable
}

/**
 * Deterministic evidence producer consumed by the local AI diagnostics path.
 * The AI may classify and learn from the result, but must not override routing
 * or produce a new direction.
 */
internal object ManeuverAlignmentValidator {
    fun compare(observation: ManeuverAlignmentObservation): ManeuverAlignmentResult {
        val displayed = observation.displayed
            ?: return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.NotComparable,
                "Kein angezeigtes Manöver vorhanden.",
            )
        val spoken = observation.spoken
            ?: return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.NotComparable,
                "Noch keine Sprachansage aufgezeichnet.",
            )

        if (displayed.beginShapeIndex != null && spoken.beginShapeIndex != null &&
            displayed.beginShapeIndex != spoken.beginShapeIndex
        ) {
            return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.SequenceMismatch,
                "Anzeige und Ansage verwenden unterschiedliche Manöverpositionen.",
            )
        }

        if (displayed.endShapeIndex != null && spoken.endShapeIndex != null &&
            displayed.endShapeIndex != spoken.endShapeIndex
        ) {
            return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.SequenceMismatch,
                "Anzeige und Ansage verwenden unterschiedliche Manöverenden.",
            )
        }

        if (displayed.maneuverType != spoken.maneuverType ||
            !displayed.instruction.equals(spoken.instruction, ignoreCase = true)
        ) {
            return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.ContentMismatch,
                "Richtung oder Anweisung stimmen zwischen Anzeige und Sprache nicht überein.",
            )
        }

        val spokenAt = observation.spokenAtElapsedRealtimeMs
        if (spokenAt != null &&
            spokenAt < observation.displayedAtElapsedRealtimeMs
        ) {
            return ManeuverAlignmentResult(
                ManeuverAlignmentStatus.StaleSpeech,
                "Die Sprachansage stammt zeitlich vor dem angezeigten Manöver.",
            )
        }

        return ManeuverAlignmentResult(
            ManeuverAlignmentStatus.Aligned,
            "Anzeige und Sprachansage referenzieren dasselbe Manöver.",
        )
    }
}
