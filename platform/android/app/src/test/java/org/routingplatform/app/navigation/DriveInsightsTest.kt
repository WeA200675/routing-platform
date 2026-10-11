package org.routingplatform.app.navigation

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveInsightsTest {
    @Test
    fun statisticsAggregateCompletedDriveMetrics() {
        val stats = DriveStatistics.from(listOf(
            CompletedDrive(1_000, 10_000, 900, "fastest"),
            CompletedDrive(2_000, 20_000, 1_800, "fastest"),
            CompletedDrive(3_000, 5_000, 600, "comfort"),
        ))
        assertEquals(3, stats.driveCount)
        assertEquals(35_000L, stats.totalDistanceMeters)
        assertEquals(3_300L, stats.totalDurationSeconds)
        assertEquals(1_100, stats.averageDurationSeconds)
        assertEquals("fastest", stats.commonRouteFamily)
        assertNull(DriveStatistics.from(emptyList()).averageDurationSeconds)
    }

    @Test
    fun historicalMeanUsesOnlySameRouteAndSimilarLocalDepartureTime() {
        val zone = ZoneId.of("Europe/Berlin")
        val target = Instant.parse("2026-10-05T07:30:00Z").toEpochMilli()
        val mondayOne = Instant.parse("2026-09-28T07:00:00Z").toEpochMilli()
        val mondayTwo = Instant.parse("2026-09-21T08:00:00Z").toEpochMilli()
        val mondayThree = Instant.parse("2026-09-14T07:45:00Z").toEpochMilli()
        val samples = listOf(
            HistoricalRouteObservation("home-work", mondayOne, 1_000, 1_300),
            HistoricalRouteObservation("home-work", mondayTwo, 1_000, 1_600),
            HistoricalRouteObservation("home-work", mondayThree, 1_000, 1_900),
            HistoricalRouteObservation("other-route", mondayOne, 1_000, 99_000),
        )
        val estimate = HistoricalTrafficAverager.estimateDelay(
            samples, "home-work", target, zone
        )
        assertEquals(3, estimate?.sampleCount)
        assertEquals(600, estimate?.meanDelaySeconds)
        assertNull(HistoricalTrafficAverager.estimateDelay(samples.take(2), "home-work", target, zone))
    }

    @Test
    fun departureUsesFreshLiveThenHistoricalMeanThenBaseline() {
        val now = 1_800_000_000_000L
        val arrival = now + 3_600_000L
        val report = TrafficCongestionReport(
            id = "de-1",
            provider = TrafficDataProvider.OpenConditions,
            sourceLabel = "OpenConditions Germany",
            roadLabel = "A8",
            observedAtEpochMillis = now - 30_000L,
            validUntilEpochMillis = now + 300_000L,
            estimatedDelaySeconds = 300,
            disruptionDurationSeconds = null,
            confidence = 0.85,
        )
        val live = DepartureRecommendationPolicy.recommend(
            arrival, 1_800, 300, report, now, historicalDelaySeconds = 900, historicalSampleCount = 5
        )
        assertEquals(arrival - 2_400_000L, live.recommendedDepartureEpochMillis)
        assertEquals(DepartureEstimateKind.LiveTraffic, live.estimateKind)
        assertEquals(TrafficDataProvider.OpenConditions, live.trafficSource)

        val historical = DepartureRecommendationPolicy.recommend(
            arrival, 1_800, 300, report.copy(observedAtEpochMillis = now - 600_000L),
            now, historicalDelaySeconds = 900, historicalSampleCount = 5
        )
        assertEquals(arrival - 3_000_000L, historical.recommendedDepartureEpochMillis)
        assertEquals(DepartureEstimateKind.HistoricalMean, historical.estimateKind)
        assertEquals(5, historical.historicalSampleCount)

        val baseline = DepartureRecommendationPolicy.recommend(arrival, 1_800, 300, null, now)
        assertEquals(arrival - 2_100_000L, baseline.recommendedDepartureEpochMillis)
        assertEquals(DepartureEstimateKind.BaselineOnly, baseline.estimateKind)
    }
    @Test
    fun historicalMeanDoesNotTreatSameWeekdayMidnightAsAdjacentAcrossWholeDay() {
        val zone = ZoneId.of("Europe/Berlin")
        val target = Instant.parse("2026-10-04T22:10:00Z").toEpochMilli() // Monday 00:10
        val previousEvening = Instant.parse("2026-09-28T21:50:00Z").toEpochMilli() // Monday 23:50
        val estimate = HistoricalTrafficAverager.estimateDelay(
            listOf(
                HistoricalRouteObservation("night", previousEvening, 1_000, 2_000),
                HistoricalRouteObservation("night", previousEvening - 7 * 24 * 60 * 60_000L, 1_000, 2_000),
                HistoricalRouteObservation("night", previousEvening - 14 * 24 * 60 * 60_000L, 1_000, 2_000),
            ),
            routeKey = "night",
            targetDepartureEpochMillis = target,
            zoneId = zone,
        )
        assertNull(estimate)
    }

    @Test
    fun plannedArrivalParserAcceptsFutureAndRejectsInvalidPastAndDaylightGap() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = Instant.parse("2026-10-11T10:00:00Z").toEpochMilli()
        val valid = PlannedArrivalTimeParser.parse("12.10.2026", "08:30", now, zone)
        assertEquals(
            Instant.parse("2026-10-12T06:30:00Z").toEpochMilli(),
            (valid as? PlannedArrivalParseResult.Valid)?.epochMillis,
        )
        assertEquals(
            "Bitte ein gültiges Datum im Format TT.MM.JJJJ eingeben.",
            (PlannedArrivalTimeParser.parse("31.02.2026", "08:30", now, zone)
                as? PlannedArrivalParseResult.Invalid)?.message,
        )
        assertEquals(
            "Die Ankunft muss in der Zukunft liegen.",
            (PlannedArrivalTimeParser.parse("11.10.2026", "11:00", now, zone)
                as? PlannedArrivalParseResult.Invalid)?.message,
        )
        assertEquals(
            "Diese Uhrzeit existiert wegen der Zeitumstellung nicht. Bitte eine andere Uhrzeit wählen.",
            (PlannedArrivalTimeParser.parse("29.03.2026", "02:30", now, zone)
                as? PlannedArrivalParseResult.Invalid)?.message,
        )
    }

}
