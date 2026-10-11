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

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
}

data class DrivingInterferenceEvidenceRecord(
    val eventId: String,
    val payloadDigest: String,
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
        require(sessionReference.isNotBlank() && sessionReference.length <= MAX_SESSION_REFERENCE_CHARS)
        require(source.isNotBlank() && source.length <= MAX_SOURCE_CHARS)
        require(action.isNotBlank() && action.length <= MAX_ACTION_CHARS)
        require(integrityState.isNotBlank() && integrityState.length <= MAX_INTEGRITY_CHARS)
        require(payload.size <= MAX_PAYLOAD_BYTES)

        val payloadDigest = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val canonical = listOf(
            kind.name, utcEpochMillis.toString(), elapsedRealtimeNanos.toString(),
            sessionReference, source, action, integrityState, payloadDigest,
            vehiclePosition?.let { "${it.latitude},${it.longitude},${it.accuracyM},${it.observedElapsedRealtimeNanos}" } ?: "-"
        ).joinToString("\u0000")
        val eventId = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

        return DrivingInterferenceEvidenceRecord(
            eventId = eventId,
            payloadDigest = payloadDigest,
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

    private const val MAX_SESSION_REFERENCE_CHARS = 128
    private const val MAX_SOURCE_CHARS = 128
    private const val MAX_ACTION_CHARS = 64
    private const val MAX_INTEGRITY_CHARS = 64
    private const val MAX_PAYLOAD_BYTES = 64 * 1024
}
