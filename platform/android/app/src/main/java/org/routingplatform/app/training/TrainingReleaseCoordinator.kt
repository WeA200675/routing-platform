package org.routingplatform.app.training

import org.routingplatform.app.navigation.NavigationDecisionTelemetry

data class TrainingReleaseDecision(
    val accepted: Boolean,
    val event: TrainingEvent?,
    val artifact: TrainingArtifact?,
    val reason: String,
)

internal class TrainingReleaseCoordinator(
    private val registry: TrainingArtifactRegistry,
) {
    fun prepare(
        telemetry: NavigationDecisionTelemetry,
        artifact: TrainingArtifact,
    ): TrainingReleaseDecision {
        val event = NavigationDecisionTrainingEventAdapter.toTrainingEvent(telemetry)
        if (!registry.register(artifact)) {
            return TrainingReleaseDecision(false, event, null, "Artefakt ungültig")
        }
        if (!registry.promoteCanary(artifact.id)) {
            return TrainingReleaseDecision(false, event, null, "Canary-Übergang fehlgeschlagen")
        }
        if (!registry.activate(artifact.id)) {
            return TrainingReleaseDecision(false, event, null, "Aktivierung durch Schutzgate abgelehnt")
        }
        return TrainingReleaseDecision(true, event, registry.active(), "Artefakt aktiviert")
    }
}
