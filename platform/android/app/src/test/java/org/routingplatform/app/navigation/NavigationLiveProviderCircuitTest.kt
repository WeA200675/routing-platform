package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationLiveProviderCircuitTest {
    @Test
    fun suspendsProviderAfterRepeatedFailures() {
        val circuit = NavigationLiveProviderCircuit(failureThreshold = 2, suspensionMs = 100)
        circuit.recordFailure("traffic", 1_000)
        circuit.recordFailure("traffic", 1_001)

        assertEquals(false, circuit.isAllowed("traffic", 1_050))
        assertEquals(true, circuit.isAllowed("traffic", 1_101))
    }

    @Test
    fun successResetsFailureState() {
        val circuit = NavigationLiveProviderCircuit(failureThreshold = 2)
        circuit.recordFailure("traffic", 1_000)
        circuit.recordSuccess("traffic")

        assertEquals(0, circuit.health("traffic").consecutiveFailures)
        assertEquals(true, circuit.isAllowed("traffic", 1_001))
    }

    @Test
    fun cooldownExpiryResetsFailureWindow() {
        val circuit = NavigationLiveProviderCircuit(failureThreshold = 2, suspensionMs = 100)
        circuit.recordFailure("traffic", 1_000)
        circuit.recordFailure("traffic", 1_001)
        circuit.recordFailure("traffic", 1_050)
        assertEquals(2, circuit.health("traffic").consecutiveFailures)
        assertEquals(1_101L, circuit.health("traffic").suspendedUntilEpochMs)
        circuit.recordFailure("traffic", 1_101)
        assertEquals(1, circuit.health("traffic").consecutiveFailures)
        assertEquals(true, circuit.isAllowed("traffic", 1_101))
        circuit.recordFailure("traffic", 1_102)
        assertEquals(false, circuit.isAllowed("traffic", 1_150))
    }

    @Test
    fun providerFailuresAreIsolated() {
        val circuit = NavigationLiveProviderCircuit(failureThreshold = 1)
        circuit.recordFailure("traffic", 1_000)
        assertEquals(false, circuit.isAllowed("traffic", 1_001))
        assertEquals(true, circuit.isAllowed("weather", 1_001))
    }

    @Test
    fun cooldownDeadlineCannotOverflow() {
        val circuit = NavigationLiveProviderCircuit(failureThreshold = 1, suspensionMs = 100)
        circuit.recordFailure("traffic", Long.MAX_VALUE - 2)
        assertEquals(Long.MAX_VALUE, circuit.health("traffic").suspendedUntilEpochMs)
        assertEquals(false, circuit.isAllowed("traffic", Long.MAX_VALUE - 1))
    }

    @Test
    fun rejectsInvalidConfiguration() {
        try {
            NavigationLiveProviderCircuit(failureThreshold = 0)
            throw AssertionError("Expected invalid threshold")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            NavigationLiveProviderCircuit(suspensionMs = 0)
            throw AssertionError("Expected invalid suspension")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
