package org.routingplatform.app.navigation

enum class NavigationTrainingPurpose {
    NavigationStability,
    VoiceTiming,
    MapOrientation,
    GeneralModelTraining,
}

data class NavigationTrainingMode(
    val active: Boolean = false,
    val purpose: NavigationTrainingPurpose = NavigationTrainingPurpose.GeneralModelTraining,
    val startedAtNanos: Long? = null,
    val sampleCount: Int = 0,
    val incidentCount: Int = 0,
) {
    fun start(nowNanos: Long, selectedPurpose: NavigationTrainingPurpose): NavigationTrainingMode =
        copy(
            active = true,
            purpose = selectedPurpose,
            startedAtNanos = nowNanos,
        )

    fun stop(): NavigationTrainingMode = copy(active = false)

    fun recordSample(): NavigationTrainingMode =
        if (active) copy(sampleCount = sampleCount + 1) else this

    fun recordIncident(): NavigationTrainingMode =
        if (active) copy(incidentCount = incidentCount + 1) else this
}

enum class NavigationIncidentTrigger {
    SevereNavigation,
    NavigationAborted,
}
