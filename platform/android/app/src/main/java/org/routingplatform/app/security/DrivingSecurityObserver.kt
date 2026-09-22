package org.routingplatform.app.security

/**
 * Defensive, presentation-free security observer. Callers may feed only events
 * already exposed to this app by Android APIs/permissions. It does not scan,
 * intercept packets, attribute an attacker, or perform counter-actions.
 */
data class AuthorizedDrivingSystemEvent(
    val kind: DrivingInterferenceKind,
    val utcEpochMillis: Long,
    val elapsedRealtimeNanos: Long,
    val source: String,
    val payload: ByteArray,
    val integrityState: String,
)

class DrivingSecurityObserver(
    private val store: DrivingInterferenceEvidenceStore,
    private val sessionPseudonym: (String) -> String,
) {
    fun record(
        navigationActive: Boolean,
        sessionId: String,
        event: AuthorizedDrivingSystemEvent,
        trustedVehiclePosition: EvidenceVehiclePosition? = null,
    ): DrivingInterferenceEvidenceRecord? {
        if (!navigationActive) return null
        val action = when (event.kind) {
            DrivingInterferenceKind.ConnectivityChange -> "record-only"
            DrivingInterferenceKind.InterferenceAnomaly -> "contain-local"
            DrivingInterferenceKind.IntegrityFailure -> "contain-local"
        }
        val record = DrivingInterferenceEvidence.create(
            kind = event.kind,
            utcEpochMillis = event.utcEpochMillis,
            elapsedRealtimeNanos = event.elapsedRealtimeNanos,
            sessionReference = sessionPseudonym(sessionId),
            source = event.source,
            action = action,
            integrityState = event.integrityState,
            payload = event.payload,
            vehiclePosition = trustedVehiclePosition,
        )
        return runCatching { store.append(record); record }.getOrNull()
    }
}
