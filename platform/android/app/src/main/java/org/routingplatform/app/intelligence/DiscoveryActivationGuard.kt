package org.routingplatform.app.intelligence

data class DiscoveryGuardDecision(
    val allowed: Boolean,
    val reason: String,
    val requiresReview: Boolean,
)

internal class DiscoveryActivationGuard(
    private val allowedHosts: Set<String>,
    private val trustedManifestKeys: Set<String>,
    private val maxCallsPerMinute: Int = 60,
    private val maxResponseBytes: Long = 16L * 1024L * 1024L,
) {
    private val callTimes = ArrayDeque<Long>()
    @Volatile
    private var killSwitch = false

    fun setKillSwitch(enabled: Boolean) {
        killSwitch = enabled
    }

    @Synchronized
    fun authorize(
        candidate: DiscoveryCandidate,
        manifestKeyId: String?,
        signatureVerified: Boolean,
        nowEpochMs: Long,
    ): DiscoveryGuardDecision {
        if (killSwitch) return DiscoveryGuardDecision(false, "Discovery-Kill-Switch aktiv", false)
        val host = candidate.endpoint.host?.lowercase()
        if (candidate.endpoint.scheme != "https") {
            return DiscoveryGuardDecision(false, "Nur HTTPS erlaubt", false)
        }
        if (host == null || host !in allowedHosts) {
            return DiscoveryGuardDecision(false, "Host nicht freigegeben", false)
        }
        if (manifestKeyId == null || manifestKeyId !in trustedManifestKeys || !signatureVerified) {
            return DiscoveryGuardDecision(false, "Vertrauenswürdige Signatur fehlt", false)
        }
        while (callTimes.isNotEmpty() && nowEpochMs - callTimes.first() >= 60_000L) {
            callTimes.removeFirst()
        }
        if (callTimes.size >= maxCallsPerMinute) {
            return DiscoveryGuardDecision(false, "Rate-Limit erreicht", true)
        }
        callTimes.addLast(nowEpochMs)
        return DiscoveryGuardDecision(true, "Quelle für kontrollierte Prüfung zugelassen", true)
    }

    fun maxResponseBytes(): Long = maxResponseBytes
}
