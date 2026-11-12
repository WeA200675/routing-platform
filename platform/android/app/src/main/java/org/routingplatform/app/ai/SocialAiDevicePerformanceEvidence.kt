package org.routingplatform.app.ai

/**
 * Aggregated privacy-safe performance evidence for the physical G6.20 gate.
 * No prompts, generated text, recalled memory, route facts or locations are stored.
 */
data class SocialAiDevicePerformanceEvidence(
    val sampleCount: Int,
    val coldLoadMillis: Long,
    val memoryBeforeLoadBytes: Long,
    val memoryAfterLoadBytes: Long,
    val memoryAfterUnloadBytes: Long,
    val generationLatencyMillis: List<Long>,
) {
    init {
        require(sampleCount >= 20)
        require(generationLatencyMillis.size == sampleCount)
        require(coldLoadMillis >= 0)
        require(memoryBeforeLoadBytes >= 0)
        require(memoryAfterLoadBytes >= memoryBeforeLoadBytes)
        require(memoryAfterUnloadBytes >= 0)
        require(generationLatencyMillis.all { it >= 0 })
    }

    val peakModelMemoryBytes: Long get() = memoryAfterLoadBytes - memoryBeforeLoadBytes
    val reclaimedMemoryBytes: Long get() =
        (memoryAfterLoadBytes - memoryAfterUnloadBytes).coerceAtLeast(0)

    fun percentile(percent: Int): Long {
        require(percent in 1..100)
        val sorted = generationLatencyMillis.sorted()
        val index = ((percent * sorted.size + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    val p50GenerationMillis: Long get() = percentile(50)
    val p95GenerationMillis: Long get() = percentile(95)
}

object SocialAiDevicePerformanceGate {
    fun validate(evidence: SocialAiDevicePerformanceEvidence): List<String> = buildList {
        if (evidence.reclaimedMemoryBytes <= 0) {
            add("Model unload did not demonstrate memory reclamation.")
        }
        if (evidence.p95GenerationMillis < evidence.p50GenerationMillis) {
            add("Latency percentile evidence is inconsistent.")
        }
    }
}
