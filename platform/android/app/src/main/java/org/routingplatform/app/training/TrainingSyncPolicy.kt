package org.routingplatform.app.training

data class TrainingSyncDecision(
    val shouldUpload: Boolean,
    val maxEvents: Int,
    val reason: String,
)

object TrainingSyncPolicy {
    fun decide(
        networkAvailable: Boolean,
        navigationActive: Boolean,
        queuedEvents: Int,
        batteryPercent: Int,
    ): TrainingSyncDecision {
        if (!networkAvailable) {
            return TrainingSyncDecision(false, 0, "offline: lokal puffern")
        }
        if (queuedEvents <= 0) {
            return TrainingSyncDecision(false, 0, "keine Ereignisse")
        }
        if (batteryPercent <= 10 && navigationActive) {
            return TrainingSyncDecision(false, 0, "Akku schützen")
        }
        val batch = when {
            navigationActive -> 16
            else -> 64
        }
        return TrainingSyncDecision(true, minOf(batch, queuedEvents), "Upload freigegeben")
    }
}
