package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTrafficReevaluationPolicyTest {
    private val policy =
        NavigationTrafficReevaluationPolicy(
            refreshIntervalMs = 120_000L,
            retryIntervalMs = 30_000L,
        )

    @Test(expected = IllegalArgumentException::class)
    fun futureSuccessTimestampFailsClosed() {
        policy.decide(
            nowMs = 100L,
            lastSuccessfulRefreshMs = 101L,
            lastAttemptMs = null,
            requestInFlight = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun futureAttemptTimestampFailsClosed() {
        policy.decide(
            nowMs = 100L,
            lastSuccessfulRefreshMs = null,
            lastAttemptMs = 101L,
            requestInFlight = false,
        )
    }

    @Test
    fun firstEvaluationRequestsEngineRefresh() {
        assertEquals(
            NavigationTrafficReevaluationDecision.Refresh,
            policy.decide(1_000L, null, null, false),
        )
    }

    @Test
    fun freshEngineRouteDoesNotOscillate() {
        assertEquals(
            NavigationTrafficReevaluationDecision.Hold(
                NavigationTrafficReevaluationHoldReason.Fresh
            ),
            policy.decide(
                nowMs = 100_000L,
                lastSuccessfulRefreshMs = 50_000L,
                lastAttemptMs = 50_000L,
                requestInFlight = false,
            ),
        )
    }

    @Test
    fun staleEngineRouteRequestsRefresh() {
        assertEquals(
            NavigationTrafficReevaluationDecision.Refresh,
            policy.decide(
                nowMs = 170_000L,
                lastSuccessfulRefreshMs = 50_000L,
                lastAttemptMs = 50_000L,
                requestInFlight = false,
            ),
        )
    }

    @Test
    fun providerFailureUsesRetryCooldown() {
        assertEquals(
            NavigationTrafficReevaluationDecision.Hold(
                NavigationTrafficReevaluationHoldReason.RetryCooldown
            ),
            policy.decide(
                nowMs = 125_000L,
                lastSuccessfulRefreshMs = 0L,
                lastAttemptMs = 120_000L,
                requestInFlight = false,
            ),
        )
    }

    @Test
    fun inFlightRequestCannotDuplicateRefresh() {
        assertEquals(
            NavigationTrafficReevaluationDecision.Hold(
                NavigationTrafficReevaluationHoldReason.RequestInFlight
            ),
            policy.decide(
                nowMs = 500_000L,
                lastSuccessfulRefreshMs = 0L,
                lastAttemptMs = 100_000L,
                requestInFlight = true,
            ),
        )
    }
}
