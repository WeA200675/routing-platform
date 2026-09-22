package org.routingplatform.app.security

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class DrivingInterferenceKind { ConnectivityChange, InterferenceAnomaly, IntegrityFailure }

data class EvidenceVehiclePosition(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double,
    val observedElapsedRealtimeNanos: Long,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
        require(accuracyM.isFinite() && accuracyM >= 0.0)
        require(observedElapsedRealtimeNanos >= 0L)
    }
}

data class DrivingInterferenceEvidenceRecord(
    val eventId: String,
    val kind: DrivingInterferenceKind,
    val utcEpochMillis: Long,
    val elapsedRealtimeNanos: Long,
    val sessionReference: String,
    val source: String,
    val action: String,
    val integrityState: String,
    val vehiclePosition: EvidenceVehiclePosition?,
)

/**
 * Creates a stable evidence identifier without retaining raw payload bytes.
 * The digest is evidence correlation, not an attribution mechanism.
 */
object DrivingInterferenceEvidence {
    fun create(
        kind: DrivingInterferenceKind,
        utcEpochMillis: Long,
        elapsedRealtimeNanos: Long,
        sessionReference: String,
        source: String,
        action: String,
        integrityState: String,
        payload: ByteArray,
        vehiclePosition: EvidenceVehiclePosition? = null,
    ): DrivingInterferenceEvidenceRecord {
        require(utcEpochMillis >= 0L)
        require(elapsedRealtimeNanos >= 0L)
        require(sessionReference.isNotBlank())
        require(source.isNotBlank())
        require(action.isNotBlank())
        require(integrityState.isNotBlank())

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(kind.name.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(source.toByteArray(StandardCharsets.UTF_8))
        digest.update(0)
        digest.update(payload)
        val eventId = digest.digest().joinToString("") { "%02x".format(it) }

        return DrivingInterferenceEvidenceRecord(
            eventId = eventId,
            kind = kind,
            utcEpochMillis = utcEpochMillis,
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            sessionReference = sessionReference,
            source = source,
            action = action,
            integrityState = integrityState,
            vehiclePosition = vehiclePosition,
        )
    }
}
