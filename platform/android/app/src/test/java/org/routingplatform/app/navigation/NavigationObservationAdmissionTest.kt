package org.routingplatform.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationObservationAdmissionTest {
    @Test fun requiresAllPlatformCapabilities() {
        assertTrue(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, true, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(false, true, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, false, true)))
        assertFalse(NavigationObservationAdmission.calibrationAvailable(NavigationDeviceCapabilities(true, true, false)))
    }

    @Test fun onlyHighDirectBoundedObservationIsAdmitted() {
        val valid = NavigationCalibrationObservation(
            NavigationPositionConfidence.High,
            NavigationFusionMode.DirectObservation,
            4.0,
            1L,
        )
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(confidence = NavigationPositionConfidence.Medium)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(fusionMode = NavigationFusionMode.FusedEstimate)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = null)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = Double.NaN)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = Double.POSITIVE_INFINITY)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = -0.1)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 100.1)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(elapsedRealtimeNanos = null)))
        assertFalse(NavigationObservationAdmission.directFreshObservation(valid.copy(elapsedRealtimeNanos = -1L)))
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 0.0, elapsedRealtimeNanos = 0L)))
        assertTrue(NavigationObservationAdmission.directFreshObservation(valid.copy(horizontalAccuracyM = 100.0)))
    }

    @Test fun healthSnapshotFailsClosedWithoutObservation() {
        val capabilities = NavigationDeviceCapabilities(true, true, true)
        val health = NavigationObservationAdmission.healthSnapshot(capabilities, null)

        assertTrue(health.calibrationAvailable)
        assertFalse(health.directFreshObservationAvailable)
    }

    @Test fun healthSnapshotReflectsSemanticAdmissionOnly() {
        val valid = NavigationCalibrationObservation(
            NavigationPositionConfidence.High,
            NavigationFusionMode.DirectObservation,
            4.0,
            1L,
        )
        val available = NavigationObservationAdmission.healthSnapshot(
            NavigationDeviceCapabilities(true, true, true),
            valid,
        )
        val unavailable = NavigationObservationAdmission.healthSnapshot(
            NavigationDeviceCapabilities(false, true, true),
            valid.copy(horizontalAccuracyM = 101.0),
        )

        assertTrue(available.calibrationAvailable)
        assertTrue(available.directFreshObservationAvailable)
        assertFalse(unavailable.calibrationAvailable)
        assertFalse(unavailable.directFreshObservationAvailable)
    }
    @Test fun sharedParityCorpusMatchesAndroidSemantics() {
        var dir = java.io.File(System.getProperty("user.dir")).absoluteFile
        var fixture: java.io.File? = null
        repeat(8) {
            val candidate = java.io.File(dir, "platform/shared/parity/navigation-admission-fixtures.csv")
            if (candidate.isFile) fixture = candidate
            dir = dir.parentFile ?: dir
        }
        val file = requireNotNull(fixture) { "shared parity corpus not found" }
        file.readLines().map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val p = line.split(",")
                when (p[0]) {
                    "capability" -> {
                        require(p.size == 5)
                        val actual = NavigationObservationAdmission.calibrationAvailable(
                            NavigationDeviceCapabilities(p[1].toBoolean(), p[2].toBoolean(), p[3].toBoolean())
                        )
                        assertTrue("fixture: $line", actual == p[4].toBoolean())
                    }
                    "observation" -> {
                        require(p.size == 6)
                        val confidence = when (p[1]) {
                            "high" -> NavigationPositionConfidence.High
                            "medium" -> NavigationPositionConfidence.Medium
                            "low" -> NavigationPositionConfidence.Low
                            else -> error("unknown confidence: ${p[1]}")
                        }
                        val fusion = when (p[2]) {
                            "direct" -> NavigationFusionMode.DirectObservation
                            "fused" -> NavigationFusionMode.FusedEstimate
                            "dead_reckoning" -> NavigationFusionMode.DeadReckoning
                            else -> error("unknown fusion: ${p[2]}")
                        }
                        val observation = NavigationCalibrationObservation(
                            confidence, fusion, p[3].toDouble(),
                            if (p[4].toBoolean()) 1L else null,
                        )
                        assertTrue("fixture: $line",
                            NavigationObservationAdmission.directFreshObservation(observation) == p[5].toBoolean())
                    }
                    else -> error("unknown fixture type: ${p[0]}")
                }
            }
    }
}
