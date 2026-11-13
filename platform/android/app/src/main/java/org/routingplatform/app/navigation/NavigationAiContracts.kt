package org.routingplatform.app.navigation

enum class AiDecisionOrigin { LOCAL, CLOUD }
enum class AiDecisionMode { ACT, ADVISE }
enum class AiDecisionKind { CONTINUE, REROUTE, WAIT_FOR_EVIDENCE }

data class NavigationTwinState(
    val sessionId: String,
    val routeVersion: Long,
    val position: Any?,
    val positionAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val activeProviderVersions: Map<String, String>,
    val offline: Boolean,
    val lastDecisionEventId: String?,
)

data class NavigationDecisionEnvelope(
    val eventId: String,
    val sessionId: String,
    val routeVersion: Long,
    val capturedAtEpochMs: Long,
    val origin: AiDecisionOrigin,
    val mode: AiDecisionMode,
    val kind: AiDecisionKind,
    val confidence: Double,
    val evidence: Set<String>,
    val expiresAtEpochMs: Long,
    val safetyClass: Int,
    val modelVersion: String,
)

data class DecisionRejection(val reason: String)

object DualAiDecisionArbitrator {
    data class Result(
        val accepted: NavigationDecisionEnvelope?,
        val rejection: DecisionRejection?
    )

    fun arbitrate(
        nowEpochMs: Long,
        twin: NavigationTwinState,
        local: NavigationDecisionEnvelope,
        cloud: NavigationDecisionEnvelope?,
    ): Result {
        if (local.expiresAtEpochMs < nowEpochMs) {
            return Result(null, DecisionRejection("local-expired"))
        }
        return Result(local, null)
    }
}
