package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sqrt

class NavigationWifiRttPositioningTest {
    private val anchorPosition = RoutePoint(47.0, 9.0)
    private val gpsPosition = RoutePoint(47.0001, 9.0001)
    private val sourceId = NavigationPrivacySafeRadioId("0123456789abcdef")

    @Test
    fun accessPointLocationNeedsFourDistinctGoodGpsCrossChecks() {
        val verifier = NavigationWifiRttAnchorVerifier()
        val results = (1..4).map { index ->
            verifier.observe(
                observations = listOf(observation(index * 1_000_000_000L)),
                gpsPosition = gpsPosition,
                gpsAccuracyM = 3.0,
                gpsElapsedRealtimeNanos = index * 1_000_000_000L,
            )
        }

        assertTrue(results.take(3).all { it.isEmpty() })
        assertEquals(1, results.last().size)
        assertEquals(4, results.last().single().confirmationCount)
    }

    @Test
    fun weakGpsCannotConfirmAnAccessPointLocation() {
        val verifier = NavigationWifiRttAnchorVerifier()
        repeat(6) { index ->
            verifier.observe(
                observations = listOf(observation((index + 1L) * 1_000_000_000L)),
                gpsPosition = gpsPosition,
                gpsAccuracyM = 10.0,
                gpsElapsedRealtimeNanos = (index + 1L) * 1_000_000_000L,
            )
        }
        assertTrue(verifier.verifiedAnchors().isEmpty())
    }

    @Test
    fun threeVerifiedNonCollinearRttAnchorsEstimatePosition() {
        val target = RoutePoint(47.00007, 9.00008)
        val anchors = listOf(
            VerifiedNavigationWifiRttAnchor(
                NavigationPrivacySafeRadioId("aaaaaaaaaaaaaaaa"),
                RoutePoint(47.0, 9.0),
                1.0,
                4,
            ),
            VerifiedNavigationWifiRttAnchor(
                NavigationPrivacySafeRadioId("bbbbbbbbbbbbbbbb"),
                RoutePoint(47.0002, 9.0),
                1.0,
                4,
            ),
            VerifiedNavigationWifiRttAnchor(
                NavigationPrivacySafeRadioId("cccccccccccccccc"),
                RoutePoint(47.0, 9.0003),
                1.0,
                4,
            ),
        )
        val observations = anchors.map { anchor ->
            NavigationRadioObservation(
                technology = NavigationRadioTechnology.WifiRtt,
                sourceId = anchor.sourceId,
                rssiDbm = -55,
                frequencyMhz = 5_000,
                distanceM = distanceMeters(target, anchor.position),
                distanceStdDevM = 0.5,
                elapsedRealtimeNanos = 10_000_000_000L,
                anchorPosition = anchor.position,
                anchorPositionUncertaintyM = anchor.uncertaintyM,
            )
        }

        val estimate = NavigationWifiRttPositionEstimator().estimate(
            observations = observations,
            verifiedAnchors = anchors,
            nowElapsedRealtimeNanos = 10_100_000_000L,
        )

        assertNotNull(estimate)
        assertEquals(3, estimate!!.usedAnchorCount)
        assertTrue(distanceMeters(target, estimate.position) < 2.0)
        assertTrue(estimate.horizontalAccuracyM <= 25.0)
    }

    @Test
    fun collinearAnchorsCannotProducePositionEstimate() {
        val anchors = listOf(
            verified("aaaaaaaaaaaaaaaa", RoutePoint(47.0, 9.0)),
            verified("bbbbbbbbbbbbbbbb", RoutePoint(47.0001, 9.0)),
            verified("cccccccccccccccc", RoutePoint(47.0002, 9.0)),
        )
        val target = RoutePoint(47.00008, 9.00005)
        val observations = anchors.map { anchor ->
            NavigationRadioObservation(
                technology = NavigationRadioTechnology.WifiRtt,
                sourceId = anchor.sourceId,
                rssiDbm = null,
                frequencyMhz = null,
                distanceM = distanceMeters(target, anchor.position),
                distanceStdDevM = 0.5,
                elapsedRealtimeNanos = 10_000_000_000L,
                anchorPosition = anchor.position,
                anchorPositionUncertaintyM = 1.0,
            )
        }
        assertNull(
            NavigationWifiRttPositionEstimator().estimate(
                observations,
                anchors,
                10_100_000_000L,
            ),
        )
    }

    private fun observation(timestampNanos: Long) =
        NavigationRadioObservation(
            technology = NavigationRadioTechnology.WifiRtt,
            sourceId = sourceId,
            rssiDbm = -50,
            frequencyMhz = 5_000,
            distanceM = distanceMeters(gpsPosition, anchorPosition),
            distanceStdDevM = 0.5,
            elapsedRealtimeNanos = timestampNanos,
            anchorPosition = anchorPosition,
            anchorPositionUncertaintyM = 1.0,
        )

    private fun verified(id: String, point: RoutePoint) =
        VerifiedNavigationWifiRttAnchor(
            NavigationPrivacySafeRadioId(id),
            point,
            1.0,
            4,
        )

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val meanLatitude = (a.latitude + b.latitude) / 2.0 * Math.PI / 180.0
        val east = (b.longitude - a.longitude) * Math.PI / 180.0 *
            6_371_008.8 * cos(meanLatitude)
        val north = (b.latitude - a.latitude) * Math.PI / 180.0 * 6_371_008.8
        return sqrt(east * east + north * north)
    }
}
