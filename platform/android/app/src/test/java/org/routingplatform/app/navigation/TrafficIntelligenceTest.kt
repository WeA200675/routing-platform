package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class TrafficIntelligenceTest {
    private val now = 1_800_000_000_000L
    private val report = TrafficCongestionReport(
        id = "event-1",
        provider = TrafficDataProvider.SwissFedro,
        sourceLabel = "FEDRO traffic situations",
        roadLabel = "A13",
        observedAtEpochMillis = now - 30_000L,
        validUntilEpochMillis = now + 120_000L,
        estimatedDelaySeconds = 420,
        disruptionDurationSeconds = 1_800,
        confidence = 0.9,
    )

    @Test
    fun onlyFreshExplicitDelayBecomesCongestionEta() {
        assertEquals(7, report.delayMinutesOrNull(now))
        assertNull(report.copy(observedAtEpochMillis = now - 6 * 60_000L).delayMinutesOrNull(now))
        assertNull(report.copy(estimatedDelaySeconds = null).delayMinutesOrNull(now))
    }

    @Test
    fun unknownOrStaleTrafficCannotCreateDetourOffer() {
        assertNull(
            TrafficDetourOfferPolicy.create(
                report = report.copy(estimatedDelaySeconds = null),
                currentRemainingDurationSeconds = 1_800,
                alternativeRemainingDurationSeconds = 1_000,
                nowEpochMillis = now,
                nowElapsedRealtimeMs = 10_000,
            )
        )
        assertNull(
            TrafficDetourOfferPolicy.create(
                report = report.copy(observedAtEpochMillis = now - 600_000),
                currentRemainingDurationSeconds = 1_800,
                alternativeRemainingDurationSeconds = 1_000,
                nowEpochMillis = now,
                nowElapsedRealtimeMs = 10_000,
            )
        )
    }

    @Test
    fun detourOfferExpiresAfterFifteenSecondsAndDefaultsToOriginalRoute() {
        val offer = TrafficDetourOfferPolicy.create(
            report = report,
            currentRemainingDurationSeconds = 1_800,
            alternativeRemainingDurationSeconds = 1_200,
            nowEpochMillis = now,
            nowElapsedRealtimeMs = 10_000,
        )
        assertNotNull(offer)
        offer!!
        assertEquals(17, offer.estimatedSavedSeconds / 60)
        assertEquals(TrafficDetourDecision.KeepCurrentRoute,
            TrafficDetourOfferPolicy.resolve(offer, 11_000, accepted = false))
        assertEquals(TrafficDetourDecision.AcceptAlternative,
            TrafficDetourOfferPolicy.resolve(offer, 20_000, accepted = true))
        assertEquals(TrafficDetourDecision.Expired,
            TrafficDetourOfferPolicy.resolve(offer, 25_000, accepted = true))
    }
}
