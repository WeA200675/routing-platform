package org.routingplatform.app.ai

/**
 * Recorder for timing/memory measurements from the physical-device run.
 * Callers provide process-memory samples; no user or inference content is retained.
 */
class SocialAiDevicePerformanceRecorder {
    private var coldLoadMillis: Long? = null
    private var memoryBeforeLoadBytes: Long? = null
    private var memoryAfterLoadBytes: Long? = null
    private var memoryAfterUnloadBytes: Long? = null
    private val generationLatencies = mutableListOf<Long>()

    fun recordColdLoad(durationMillis: Long, memoryBeforeBytes: Long, memoryAfterBytes: Long) {
        require(durationMillis >= 0)
        require(memoryBeforeBytes >= 0)
        require(memoryAfterBytes >= memoryBeforeBytes)
        coldLoadMillis = durationMillis
        memoryBeforeLoadBytes = memoryBeforeBytes
        memoryAfterLoadBytes = memoryAfterBytes
    }

    fun recordGeneration(durationMillis: Long) {
        require(durationMillis >= 0)
        generationLatencies += durationMillis
    }

    fun recordUnload(memoryAfterBytes: Long) {
        require(memoryAfterBytes >= 0)
        memoryAfterUnloadBytes = memoryAfterBytes
    }

    fun snapshot(): SocialAiDevicePerformanceEvidence {
        require(generationLatencies.size >= 20) { "At least 20 generation samples are required." }
        return SocialAiDevicePerformanceEvidence(
            sampleCount = generationLatencies.size,
            coldLoadMillis = requireNotNull(coldLoadMillis) { "Cold-load timing is required." },
            memoryBeforeLoadBytes = requireNotNull(memoryBeforeLoadBytes),
            memoryAfterLoadBytes = requireNotNull(memoryAfterLoadBytes),
            memoryAfterUnloadBytes = requireNotNull(memoryAfterUnloadBytes) {
                "Post-unload memory sample is required."
            },
            generationLatencyMillis = generationLatencies.toList(),
        )
    }
}
