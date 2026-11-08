package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationDecisionTelemetry

object NavigationDecisionTrainingEventAdapter {
    fun toTrainingEvent(
        telemetry: NavigationDecisionTelemetry,
        sessionId: String? = null,
        routeId: String? = null,
    ): TrainingEvent {
        val event = NavigationDecisionTrainingEventFactory.create(telemetry)
        val payload = buildString {
            append("{")
            append("\"watchdog_status\":\"").append(event.watchdogStatus).append("\",")
            append("\"live_action\":\"").append(event.liveAction).append("\",")
            append("\"recovery_action\":\"").append(event.recoveryAction).append("\",")
            append("\"recalculation_requested\":").append(event.recalculationRequested)
            event.reason?.let {
                append(",\"reason\":\"")
                append(it.replace("\\", "\\\\").replace("\"", "\\\""))
                append("\"")
            }
            append("}")
        }
        return TrainingEvent(
            eventId = event.eventId,
            schemaVersion = "navigation-decision-v1",
            eventType = event.eventType,
            sessionId = sessionId,
            routeId = routeId,
            capturedAtEpochMs = event.capturedAtElapsedMs,
            source = "android.navigation",
            qualityScore = if (event.recalculationRequested) 1.0 else 0.8,
            payload = payload,
            sizeBytes = payload.toByteArray().size,
        )
    }
}
