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
    @Test
    fun consensusAveragesFreshSameRoadReportsAndIgnoresDuplicatesAndStaleData() {
        val second = report.copy(
            id = "event-2",
            provider = TrafficDataProvider.Asfinag,
            sourceLabel = "ASFINAG",
            roadLabel = " a13 ",
            observedAtEpochMillis = now - 10_000L,
            estimatedDelaySeconds = 600,
            confidence = 0.8,
        )
        val stale = report.copy(
            id = "event-stale",
            observedAtEpochMillis = now - 10 * 60_000L,
            estimatedDelaySeconds = 3_600,
        )
        val wrongRoad = report.copy(id = "event-other-road", roadLabel = "A14")
        val result = TrafficDelayConsensusAverager.average(
            listOf(report, second, second, stale, wrongRoad),
            roadLabel = "A13",
            nowEpochMillis = now,
        )
        assertNotNull(result)
        result!!
        assertEquals(2, result.sampleCount)
        assertEquals(2, result.providerCount)
        assertEquals(420, result.minimumDelaySeconds)
        assertEquals(600, result.maximumDelaySeconds)
        assertEquals(499, result.meanDelaySeconds)
        assertEquals(now - 10_000L, result.latestObservedAtEpochMillis)
    }

    @Test
    fun consensusDoesNotTreatIncidentDurationAsDelayOrUseFutureData() {
        val onlyDuration = report.copy(estimatedDelaySeconds = null)
        val future = report.copy(id = "future-event", observedAtEpochMillis = now + 10_000L)
        assertNull(
            TrafficDelayConsensusAverager.average(
                listOf(onlyDuration, future),
                roadLabel = "A13",
                nowEpochMillis = now,
            )
        )
    }

}
