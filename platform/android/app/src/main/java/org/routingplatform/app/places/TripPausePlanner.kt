package org.routingplatform.app.places

data class PausePlanningInput(
    val routeDurationSeconds: Int,
    val elapsedDrivingSeconds: Int,
    val lastPauseElapsedSeconds: Int,
    val pauseIntervalSeconds: Int = 7_200,
    val nearbyRestStops: List<NavigationPoi> = emptyList(),
)

data class PauseRecommendation(
    val required: Boolean,
    val poiId: String?,
    val reason: String,
)

object TripPausePlanner {
    fun recommend(input: PausePlanningInput): PauseRecommendation {
        val drivingSincePause = input.elapsedDrivingSeconds - input.lastPauseElapsedSeconds
        val due = drivingSincePause >= input.pauseIntervalSeconds
        val routeLong = input.routeDurationSeconds >= input.pauseIntervalSeconds
        val stop = input.nearbyRestStops
            .filter { it.category == PoiCategory.REST_AREA || it.category == PoiCategory.RESTAURANT }
            .minByOrNull { it.detourSeconds ?: Int.MAX_VALUE }

        return when {
            due && stop != null ->
                PauseRecommendation(true, stop.id, "Pausenintervall erreicht")
            due ->
                PauseRecommendation(true, null, "Pausenintervall erreicht, kein geeigneter POI verfügbar")
            routeLong ->
                PauseRecommendation(false, stop?.id, "Pausenpunkt für lange Fahrt vormerken")
            else ->
                PauseRecommendation(false, null, "Keine Pause erforderlich")
        }
    }
}
