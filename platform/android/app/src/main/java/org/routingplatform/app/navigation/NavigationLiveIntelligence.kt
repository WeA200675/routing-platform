package org.routingplatform.app.navigation

enum class LiveEventKind {
    HAZARD,
    ROAD_CLOSURE,
    ACCIDENT,
    TRAFFIC_JAM,
    WEATHER,
    POI_UPDATE
}

data class NavigationLiveEvent(
    val id: String,
    val kind: LiveEventKind,
    val latitude: Double,
    val longitude: Double,
    val directionDegrees: Double? = null,
    val severity: Int = 0,
    val confidence: Double = 0.0,
    val capturedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val source: String,
    val signatureBase64: String? = null,
    val signatureKeyId: String? = null,
) {
    fun isValid(nowEpochMs: Long): Boolean =
        latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            confidence in 0.0..1.0 &&
            severity in 0..5 &&
            validUntilEpochMs >= nowEpochMs
}

data class LiveEventDecision(
    val event: NavigationLiveEvent,
    val priorityScore: Double,
)

object NavigationLiveEventPrioritizer {
    fun prioritize(
        events: List<NavigationLiveEvent>,
        nowEpochMs: Long,
    ): List<LiveEventDecision> =
        events.asSequence()
            .filter { it.isValid(nowEpochMs) }
            .map { event ->
                LiveEventDecision(
                    event = event,
                    priorityScore = basePriority(event.kind) +
                        event.severity * 10.0 +
                        event.confidence * 5.0 +
                        freshness(event, nowEpochMs),
                )
            }
            .sortedByDescending { it.priorityScore }
            .toList()

    private fun basePriority(kind: LiveEventKind): Double =
        when (kind) {
            LiveEventKind.HAZARD -> 100.0
            LiveEventKind.ROAD_CLOSURE -> 95.0
            LiveEventKind.ACCIDENT -> 90.0
            LiveEventKind.TRAFFIC_JAM -> 70.0
            LiveEventKind.WEATHER -> 60.0
            LiveEventKind.POI_UPDATE -> 20.0
        }

    private fun freshness(event: NavigationLiveEvent, nowEpochMs: Long): Double {
        val lifetime = (event.validUntilEpochMs - event.capturedAtEpochMs).coerceAtLeast(1L)
        val remaining = (event.validUntilEpochMs - nowEpochMs).coerceAtLeast(0L)
        return 10.0 * remaining.toDouble() / lifetime.toDouble()
    }
}
