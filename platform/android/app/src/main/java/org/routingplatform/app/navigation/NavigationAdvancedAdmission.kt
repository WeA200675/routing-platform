package org.routingplatform.app.navigation

data class OfflineRoutingProvenance(
    val sourceId: String,
    val sha256: String,
)

object OfflineRoutingProvenanceAdmission {
    fun verified(provenance: OfflineRoutingProvenance?): Boolean {
        if (provenance == null || provenance.sourceId.isBlank()) return false
        return provenance.sha256.length == 64 &&
            provenance.sha256.all { it in '0'..'9' || it in 'a'..'f' }
    }
}

data class PersistedNavigationState(
    val schemaVersion: Int,
    val sessionId: String,
    val savedAtElapsedRealtimeNanos: Long,
)

object NavigationStateRestoreAdmission {
    const val SCHEMA_VERSION = 1

    fun mayRestore(
        state: PersistedNavigationState?,
        nowElapsedRealtimeNanos: Long,
        maximumAgeNanos: Long,
    ): Boolean {
        if (state == null || state.schemaVersion != SCHEMA_VERSION || state.sessionId.isBlank()) return false
        if (nowElapsedRealtimeNanos < 0 || maximumAgeNanos < 0) return false
        if (state.savedAtElapsedRealtimeNanos < 0 || state.savedAtElapsedRealtimeNanos > nowElapsedRealtimeNanos) return false
        return nowElapsedRealtimeNanos - state.savedAtElapsedRealtimeNanos <= maximumAgeNanos
    }
}
