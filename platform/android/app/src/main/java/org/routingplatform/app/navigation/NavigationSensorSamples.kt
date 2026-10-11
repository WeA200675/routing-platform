package org.routingplatform.app.navigation

data class NavigationVector3Sample(
    val x: Double,
    val y: Double,
    val z: Double,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(
            x.isFinite() &&
                y.isFinite() &&
                z.isFinite()
        ) {
            "Vector sample values must be finite."
        }

        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

data class NavigationBearingSample(
    val bearingDegrees: Double,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(
            bearingDegrees.isFinite() &&
                bearingDegrees >= 0.0 &&
                bearingDegrees < 360.0
        ) {
            "Bearing must be in [0, 360)."
        }

        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

data class NavigationGnssEvidenceSample(
    val evidence: NavigationGnssEvidence,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

data class NavigationSensorEvidenceSnapshot(
    val gnss:
        NavigationGnssEvidenceSample?,

    val acceleration:
        NavigationVector3Sample?,

    val gyroscope:
        NavigationVector3Sample?,

    val bearing:
        NavigationBearingSample?,
)