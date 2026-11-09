package org.routingplatform.app.intelligence

data class DiscoveryAnomaly(
    val candidateId: String,
    val kind: Kind,
    val value: Double,
    val baseline: Double?,
    val reason: String,
) {
    enum class Kind { LATENCY_SPIKE, ERROR_SPIKE, FRESHNESS_DROP, VOLUME_SPIKE }
}

internal class DiscoveryAnomalyDetector(
    private val historyLimit: Int = 30,
    private val deviationFactor: Double = 3.0,
) {
    private val histories = mutableMapOf<String, ArrayDeque<Double>>()

    @Synchronized
    fun observe(
        candidateId: String,
        latencyMs: Long,
        errorRate: Double,
        freshnessSeconds: Long?,
        eventCount: Int,
    ): List<DiscoveryAnomaly> {
        val values = histories.getOrPut(candidateId) { ArrayDeque() }
        val signal = latencyMs.toDouble()
        val baseline = values.average().takeIf { values.isNotEmpty() }
        values.addLast(signal)
        while (values.size > historyLimit) values.removeFirst()
        val anomalies = mutableListOf<DiscoveryAnomaly>()
        if (baseline != null && signal > baseline * deviationFactor) {
            anomalies += DiscoveryAnomaly(
                candidateId,
                DiscoveryAnomaly.Kind.LATENCY_SPIKE,
                signal,
                baseline,
                "Latenz deutlich über dem Verlauf",
            )
        }
        if (errorRate >= 0.25) {
            anomalies += DiscoveryAnomaly(
                candidateId,
                DiscoveryAnomaly.Kind.ERROR_SPIKE,
                errorRate,
                null,
                "Fehlerquote über Sicherheitsgrenze",
            )
        }
        if (freshnessSeconds != null && freshnessSeconds > 900) {
            anomalies += DiscoveryAnomaly(
                candidateId,
                DiscoveryAnomaly.Kind.FRESHNESS_DROP,
                freshnessSeconds.toDouble(),
                900.0,
                "Daten nicht mehr ausreichend aktuell",
            )
        }
        if (eventCount > 10_000) {
            anomalies += DiscoveryAnomaly(
                candidateId,
                DiscoveryAnomaly.Kind.VOLUME_SPIKE,
                eventCount.toDouble(),
                null,
                "Ungewöhnlich große Ereignismenge",
            )
        }
        return anomalies
    }
}
