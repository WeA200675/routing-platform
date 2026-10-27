package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationAiObservationGateTest {
    @Test
    fun integrityFailureIsRejectedBeforeLocalModel() {
        var modelCalled = false
        val gate = NavigationAiObservationGate(
            model = LocalNavigationAnomalyModel {
                modelCalled = true
                NavigationAiAssessment(
                    NavigationAiVerdict.Plausible,
                    1.0,
                    emptySet(),
                )
            },
        )

        val result = gate.observe(
            NavigationAiObservation(
                timestampNanos = 1L,
                latitude = 52.0,
                longitude = 13.0,
                speedMps = null,
                bearingDegrees = null,
                integrityIssues = setOf(PositionIntegrityIssue.PoorHorizontalAccuracy),
            ),
        )

        assertEquals(NavigationAiVerdict.Reject, result.verdict)
        assertTrue(!modelCalled)
    }

    @Test
    fun localModelRemainsAdvisory() {
        val gate = NavigationAiObservationGate(
            model = LocalNavigationAnomalyModel {
                NavigationAiAssessment(
                    NavigationAiVerdict.Reject,
                    0.9,
                    setOf("model_signal"),
                )
            },
        )

        val result = gate.observe(
            NavigationAiObservation(
                timestampNanos = 1L,
                latitude = 52.0,
                longitude = 13.0,
                speedMps = 8.0,
                bearingDegrees = 90.0,
                integrityIssues = emptySet(),
            ),
        )

        assertEquals(NavigationAiVerdict.Reject, result.verdict)
        assertEquals(setOf("model_signal"), result.reasons)
    }
}
