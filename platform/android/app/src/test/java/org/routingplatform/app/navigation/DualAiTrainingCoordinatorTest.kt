package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DualAiTrainingCoordinatorTest {
    @Test
    fun localDecisionRemainsActiveWhenCloudIsOffline() {
        val local = NavigationDecisionEnvelope(
            eventId = "local-1",
            sessionId = "s1",
            routeVersion = 1,
            capturedAtEpochMs = 100,
            origin = AiDecisionOrigin.LOCAL,
            mode = AiDecisionMode.ACT,
            kind = AiDecisionKind.CONTINUE,
            confidence = 0.9,
            evidence = setOf("gps", "route"),
            expiresAtEpochMs = 10_000,
            safetyClass = 1,
            modelVersion = "local-test",
        )
        val result = DualAiTrainingCoordinator().evaluate(
            nowEpochMs = 200,
            twin = NavigationTwinState(
                sessionId = "s1",
                routeVersion = 1,
                position = null,
                positionAccuracyMeters = 4.0,
                speedMetersPerSecond = 10.0,
                activeProviderVersions = emptyMap(),
                offline = true,
                lastDecisionEventId = null,
            ),
            local = local,
        )
        assertNotNull(result.accepted)
        assertEquals(AiDecisionOrigin.LOCAL, result.accepted?.origin)
        assertEquals(null, result.cloudAdvice)
    }
}
