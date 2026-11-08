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
}
