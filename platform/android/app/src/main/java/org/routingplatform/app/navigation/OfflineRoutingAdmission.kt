package org.routingplatform.app.navigation

/**
 * P20 platform-neutral offline routing-data admission.
 *
 * Offline data is authoritative only when its identity is explicit, integrity
 * has been verified and its age is within the caller's reviewed freshness
 * budget. Unknown, corrupt, future-dated or stale data fails closed.
 */
data class OfflineRoutingDataset(
    val datasetId: String,
    val version: String,
    val capturedAtElapsedRealtimeNanos: Long,
    val integrityVerified: Boolean,
)

enum class OfflineRoutingAvailability {
    Available,
    Missing,
    InvalidIdentity,
    IntegrityFailure,
    InvalidClock,
    InvalidFreshnessBudget,
    InvalidTimestamp,
    FutureDated,
    Stale,
}

object OfflineRoutingAdmission {
    fun availability(
        dataset: OfflineRoutingDataset?,
        nowElapsedRealtimeNanos: Long,
        maximumAgeNanos: Long,
    ): OfflineRoutingAvailability {
        if (dataset == null) return OfflineRoutingAvailability.Missing
        if (dataset.datasetId.isBlank() || dataset.version.isBlank()) {
            return OfflineRoutingAvailability.InvalidIdentity
        }
        if (!dataset.integrityVerified) return OfflineRoutingAvailability.IntegrityFailure
        if (nowElapsedRealtimeNanos < 0L) {
            return OfflineRoutingAvailability.InvalidClock
        }
        if (maximumAgeNanos < 0L) {
            return OfflineRoutingAvailability.InvalidFreshnessBudget
        }
        if (dataset.capturedAtElapsedRealtimeNanos < 0L) {
            return OfflineRoutingAvailability.InvalidTimestamp
        }
        if (dataset.capturedAtElapsedRealtimeNanos > nowElapsedRealtimeNanos) {
            return OfflineRoutingAvailability.FutureDated
        }

        val age = nowElapsedRealtimeNanos - dataset.capturedAtElapsedRealtimeNanos
        return if (age <= maximumAgeNanos) {
            OfflineRoutingAvailability.Available
        } else {
            OfflineRoutingAvailability.Stale
        }
    }

    fun mayRouteOffline(
        dataset: OfflineRoutingDataset?,
        nowElapsedRealtimeNanos: Long,
        maximumAgeNanos: Long,
    ): Boolean =
        availability(dataset, nowElapsedRealtimeNanos, maximumAgeNanos) ==
            OfflineRoutingAvailability.Available
}


data class OfflineRoutingRecoveryState(
    val activeDatasetId: String?,
    val activeVersion: String?,
    val availability: OfflineRoutingAvailability,
) {
    val routingAvailable: Boolean
        get() = availability == OfflineRoutingAvailability.Available
}

object OfflineRoutingRecovery {
    fun recover(
        dataset: OfflineRoutingDataset?,
        nowElapsedRealtimeNanos: Long,
        maximumAgeNanos: Long,
    ): OfflineRoutingRecoveryState {
        val availability = OfflineRoutingAdmission.availability(
            dataset,
            nowElapsedRealtimeNanos,
            maximumAgeNanos,
        )
        return if (availability == OfflineRoutingAvailability.Available && dataset != null) {
            OfflineRoutingRecoveryState(
                activeDatasetId = dataset.datasetId,
                activeVersion = dataset.version,
                availability = availability,
            )
        } else {
            // Never retain identity from data that failed admission.
            OfflineRoutingRecoveryState(
                activeDatasetId = null,
                activeVersion = null,
                availability = availability,
            )
        }
    }
}
