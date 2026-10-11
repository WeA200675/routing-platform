package org.routingplatform.app.navigation

/**
 * Provider-neutral report received from an explicitly configured live feed.
 * A plain route-engine response is never traffic evidence.
 */
enum class TrafficDataProvider {
    OpenConditions,
    SwissFedro,
    Asfinag,
    SouthTyrolOpenDataHub,
}

data class TrafficCongestionReport(
    val id: String,
    val provider: TrafficDataProvider,
    val sourceLabel: String,
    val roadLabel: String?,
    val observedAtEpochMillis: Long,
    val validUntilEpochMillis: Long?,
    val estimatedDelaySeconds: Int?,
    val disruptionDurationSeconds: Int?,
    val confidence: Double,
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,120}")))
        require(sourceLabel.isNotBlank() && sourceLabel.length <= 120)
        require(roadLabel == null || roadLabel.isNotBlank() && roadLabel.length <= 160)
        require(observedAtEpochMillis >= 0L)
        require(validUntilEpochMillis == null || validUntilEpochMillis >= observedAtEpochMillis)
        require(estimatedDelaySeconds == null || estimatedDelaySeconds in 0..86_400)
        require(disruptionDurationSeconds == null || disruptionDurationSeconds in 0..604_800)
        require(confidence.isFinite() && confidence in 0.0..1.0)
    }
}

sealed interface TrafficReportStatus {
    data class Current(val ageSeconds: Long) : TrafficReportStatus
    data object MissingEstimate : TrafficReportStatus
    data object Stale : TrafficReportStatus
    data object NotYetValid : TrafficReportStatus
    data object Expired : TrafficReportStatus
}

data class TrafficReportFreshnessPolicy(
    val maximumAgeMillis: Long = 5 * 60_000L,
) {
    init {
        require(maximumAgeMillis in 30_000L..3_600_000L)
    }

    fun evaluate(report: TrafficCongestionReport, nowEpochMillis: Long): TrafficReportStatus {
        require(nowEpochMillis >= 0L)
        if (report.observedAtEpochMillis > nowEpochMillis) return TrafficReportStatus.NotYetValid
        if (report.validUntilEpochMillis != null && nowEpochMillis > report.validUntilEpochMillis) {
            return TrafficReportStatus.Expired
        }
        val age = nowEpochMillis - report.observedAtEpochMillis
        if (age > maximumAgeMillis) return TrafficReportStatus.Stale
        if (report.estimatedDelaySeconds == null && report.disruptionDurationSeconds == null) {
            return TrafficReportStatus.MissingEstimate
        }
        return TrafficReportStatus.Current(age / 1_000L)
    }
}

/** Event duration is not the same as time lost in a queue. */
fun TrafficCongestionReport.delayMinutesOrNull(
    nowEpochMillis: Long,
    policy: TrafficReportFreshnessPolicy = TrafficReportFreshnessPolicy(),
): Int? {
    if (policy.evaluate(this, nowEpochMillis) !is TrafficReportStatus.Current) return null
    return estimatedDelaySeconds?.let { kotlin.math.ceil(it / 60.0).toInt() }
}

data class TrafficDetourOffer(
    val id: String,
    val alternativeRouteId: String,
    val provider: TrafficDataProvider,
    val estimatedDelaySeconds: Int,
    val alternativeDurationSeconds: Int,
    val estimatedSavedSeconds: Int,
    val createdAtElapsedRealtimeMs: Long,
    val expiresAtElapsedRealtimeMs: Long,
) {
    init {
        require(id.isNotBlank() && id.length <= 120)
        require(alternativeRouteId.isNotBlank() && alternativeRouteId.length <= 120)
        require(estimatedDelaySeconds > 0 && estimatedDelaySeconds <= 86_400)
        require(alternativeDurationSeconds > 0)
        require(estimatedSavedSeconds > 0)
        require(createdAtElapsedRealtimeMs >= 0L)
        require(expiresAtElapsedRealtimeMs > createdAtElapsedRealtimeMs)
        require(expiresAtElapsedRealtimeMs - createdAtElapsedRealtimeMs <= OFFER_LIFETIME_MS)
    }

    companion object {
        const val OFFER_LIFETIME_MS = 15_000L
    }
}

enum class TrafficDetourDecision {
    KeepCurrentRoute,
    AcceptAlternative,
    Expired,
}

object TrafficDetourOfferPolicy {
    fun create(
        report: TrafficCongestionReport,
        currentRemainingDurationSeconds: Int,
        alternativeRemainingDurationSeconds: Int,
        nowEpochMillis: Long,
        nowElapsedRealtimeMs: Long,
        minimumSavingsSeconds: Int = 120,
        freshnessPolicy: TrafficReportFreshnessPolicy = TrafficReportFreshnessPolicy(),
    ): TrafficDetourOffer? {
        require(currentRemainingDurationSeconds > 0)
        require(alternativeRemainingDurationSeconds > 0)
        require(nowElapsedRealtimeMs >= 0L)
        require(minimumSavingsSeconds >= 0)
        val delay = report.estimatedDelaySeconds ?: return null
        if (freshnessPolicy.evaluate(report, nowEpochMillis) !is TrafficReportStatus.Current) return null
        val saved = currentRemainingDurationSeconds + delay - alternativeRemainingDurationSeconds
        if (saved < minimumSavingsSeconds) return null
        return TrafficDetourOffer(
            id = "${report.id}:$nowElapsedRealtimeMs",
            alternativeRouteId = "alternative:${report.id}",
            provider = report.provider,
            estimatedDelaySeconds = delay,
            alternativeDurationSeconds = alternativeRemainingDurationSeconds,
            estimatedSavedSeconds = saved,
            createdAtElapsedRealtimeMs = nowElapsedRealtimeMs,
            expiresAtElapsedRealtimeMs = nowElapsedRealtimeMs + TrafficDetourOffer.OFFER_LIFETIME_MS,
        )
    }

    fun resolve(offer: TrafficDetourOffer, nowElapsedRealtimeMs: Long, accepted: Boolean): TrafficDetourDecision {
        require(nowElapsedRealtimeMs >= 0L)
        if (nowElapsedRealtimeMs >= offer.expiresAtElapsedRealtimeMs) return TrafficDetourDecision.Expired
        return if (accepted) TrafficDetourDecision.AcceptAlternative else TrafficDetourDecision.KeepCurrentRoute
    }
}


/**
 * Combines temporally comparable, fresh delay observations for one road.
 * Freshness and provider confidence determine each sample's weight.
 */
data class TrafficDelayConsensus(
    val roadLabel: String,
    val meanDelaySeconds: Int,
    val minimumDelaySeconds: Int,
    val maximumDelaySeconds: Int,
    val sampleCount: Int,
    val providerCount: Int,
    val confidence: Double,
    val latestObservedAtEpochMillis: Long,
)

object TrafficDelayConsensusAverager {
    fun average(
        reports: Iterable<TrafficCongestionReport>,
        roadLabel: String,
        nowEpochMillis: Long,
        freshnessPolicy: TrafficReportFreshnessPolicy = TrafficReportFreshnessPolicy(),
    ): TrafficDelayConsensus? {
        require(roadLabel.isNotBlank() && roadLabel.length <= 160)
        require(nowEpochMillis >= 0L)
        val normalizedRoad = normalizeRoad(roadLabel)
        val usable = reports
            .distinctBy { it.id }
            .mapNotNull { report ->
                if (report.roadLabel?.let(::normalizeRoad) != normalizedRoad) return@mapNotNull null
                val delay = report.estimatedDelaySeconds ?: return@mapNotNull null
                val status = freshnessPolicy.evaluate(report, nowEpochMillis)
                if (status !is TrafficReportStatus.Current) return@mapNotNull null
                val freshness = (1.0 - status.ageSeconds.toDouble() /
                    (freshnessPolicy.maximumAgeMillis / 1_000.0)).coerceIn(0.05, 1.0)
                val weight = freshness * report.confidence
                if (weight <= 0.0) return@mapNotNull null
                WeightedDelay(report, delay, weight, freshness)
            }
        if (usable.isEmpty()) return null
        val totalWeight = usable.sumOf { it.weight }
        if (totalWeight <= 0.0) return null
        val mean = usable.sumOf { it.delaySeconds * it.weight } / totalWeight
        val confidenceWeight = usable.sumOf { it.freshnessWeight }
        val confidence = if (confidenceWeight > 0.0) {
            usable.sumOf { it.report.confidence * it.freshnessWeight } / confidenceWeight
        } else 0.0
        return TrafficDelayConsensus(
            roadLabel = roadLabel.trim(),
            meanDelaySeconds = mean.toInt(),
            minimumDelaySeconds = usable.minOf { it.delaySeconds },
            maximumDelaySeconds = usable.maxOf { it.delaySeconds },
            sampleCount = usable.size,
            providerCount = usable.map { it.report.provider }.distinct().size,
            confidence = confidence.coerceIn(0.0, 1.0),
            latestObservedAtEpochMillis = usable.maxOf { it.report.observedAtEpochMillis },
        )
    }

    private data class WeightedDelay(
        val report: TrafficCongestionReport,
        val delaySeconds: Int,
        val weight: Double,
        val freshnessWeight: Double,
    )

    private fun normalizeRoad(value: String): String =
        value.trim().replace(Regex("\\s+"), " ").uppercase()
}
