package org.routingplatform.app.navigation

data class ProviderHealth(
    val providerId: String,
    val consecutiveFailures: Int,
    val suspendedUntilEpochMs: Long,
)

class NavigationLiveProviderCircuit(
    private val failureThreshold: Int = 3,
    private val suspensionMs: Long = 60_000L,
) {
    private val failures = mutableMapOf<String, Int>()
    private val suspendedUntil = mutableMapOf<String, Long>()

    init {
        require(failureThreshold > 0)
        require(suspensionMs > 0)
    }

    fun isAllowed(providerId: String, nowEpochMs: Long): Boolean =
        nowEpochMs >= (suspendedUntil[providerId] ?: 0L)

    fun recordSuccess(providerId: String) {
        failures.remove(providerId)
        suspendedUntil.remove(providerId)
    }

    fun recordFailure(providerId: String, nowEpochMs: Long) {
        val count = (failures[providerId] ?: 0) + 1
        failures[providerId] = count
        if (count >= failureThreshold) {
            suspendedUntil[providerId] = nowEpochMs + suspensionMs
        }
    }

    fun health(providerId: String): ProviderHealth =
        ProviderHealth(
            providerId = providerId,
            consecutiveFailures = failures[providerId] ?: 0,
            suspendedUntilEpochMs = suspendedUntil[providerId] ?: 0L,
        )
}
