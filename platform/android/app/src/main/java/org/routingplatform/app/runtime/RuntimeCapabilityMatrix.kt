package org.routingplatform.app.runtime

/**
 * Single source of truth for runtime capabilities.
 * A capability is never reported as ready without a measured probe result.
 */
enum class RuntimeCapability {
    MAPS,
    ROUTING_NATIVE,
    OFFLINE_MAPS,
    GPS,
    LOCAL_AI,
    CLOUD_AI,
    LIVE_DATA,
    TRAINING,
}

enum class RuntimeCapabilityState {
    UNKNOWN,
    READY,
    DEGRADED,
    UNAVAILABLE,
    BLOCKED,
}

data class RuntimeCapabilityStatus(
    val capability: RuntimeCapability,
    val state: RuntimeCapabilityState,
    val measuredAtEpochMs: Long,
    val detail: String,
    val latencyMs: Long? = null,
    val version: String? = null,
)

data class RuntimeCapabilitySnapshot(
    val capturedAtEpochMs: Long,
    val statuses: List<RuntimeCapabilityStatus>,
) {
    fun status(capability: RuntimeCapability): RuntimeCapabilityStatus =
        statuses.firstOrNull { it.capability == capability }
            ?: RuntimeCapabilityStatus(
                capability = capability,
                state = RuntimeCapabilityState.UNKNOWN,
                measuredAtEpochMs = capturedAtEpochMs,
                detail = "not-probed",
            )

    fun canNavigate(): Boolean =
        status(RuntimeCapability.ROUTING_NATIVE).state in setOf(
            RuntimeCapabilityState.READY,
            RuntimeCapabilityState.DEGRADED,
        ) && status(RuntimeCapability.GPS).state != RuntimeCapabilityState.BLOCKED

    fun canUseLocalAi(): Boolean =
        status(RuntimeCapability.LOCAL_AI).state == RuntimeCapabilityState.READY
}

class RuntimeCapabilityMatrix(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile
    private var current = RuntimeCapabilitySnapshot(clock(), emptyList())

    fun snapshot(): RuntimeCapabilitySnapshot = current

    @Synchronized
    fun record(
        capability: RuntimeCapability,
        state: RuntimeCapabilityState,
        detail: String,
        latencyMs: Long? = null,
        version: String? = null,
    ): RuntimeCapabilitySnapshot {
        require(detail.isNotBlank()) { "Capability detail must not be blank." }
        val now = clock()
        val next = current.statuses.filterNot { it.capability == capability } +
            RuntimeCapabilityStatus(capability, state, now, detail, latencyMs, version)
        current = RuntimeCapabilitySnapshot(now, next.sortedBy { it.capability.ordinal })
        return current
    }

    @Synchronized
    fun recordProbe(
        capability: RuntimeCapability,
        probe: () -> ProbeResult,
    ): RuntimeCapabilitySnapshot {
        val started = clock()
        val result = runCatching { probe() }.getOrElse {
            ProbeResult(RuntimeCapabilityState.UNAVAILABLE, it.message ?: "probe-failed")
        }
        return record(
            capability = capability,
            state = result.state,
            detail = result.detail,
            latencyMs = (clock() - started).coerceAtLeast(0L),
            version = result.version,
        )
    }

    data class ProbeResult(
        val state: RuntimeCapabilityState,
        val detail: String,
        val version: String? = null,
    )
}
