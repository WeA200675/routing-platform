package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationDecisionTelemetry
import java.util.UUID

data class NavigationDecisionTrainingEvent(
    val eventId: String,
    val eventType: String,
    val capturedAtElapsedMs: Long,
    val watchdogStatus: String,
    val liveAction: String,
    val recoveryAction: String,
    val recalculationRequested: Boolean,
    val reason: String?,
)

object NavigationDecisionTrainingEventFactory {
    fun create(telemetry: NavigationDecisionTelemetry): NavigationDecisionTrainingEvent =
        NavigationDecisionTrainingEvent(
            eventId = UUID.randomUUID().toString(),
            eventType = "navigation.decision",
            capturedAtElapsedMs = telemetry.capturedAtElapsedMs,
            watchdogStatus = telemetry.watchdogStatus.name,
            liveAction = telemetry.liveAction.name,
            recoveryAction = telemetry.recoveryAction.name,
            recalculationRequested = telemetry.recalculationRequested,
            reason = telemetry.reason,
        )
}
