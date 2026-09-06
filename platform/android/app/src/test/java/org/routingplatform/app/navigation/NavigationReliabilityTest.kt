package org.routingplatform.app.navigation

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationReliabilityTest {

    @Test
    fun noSuitableEdgesIsNavigationTruthAndNeverRetried() {
        val fault =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        502,

                    responseBody =
                        "VALHALLA_ROUTE_FAILED - No suitable edges near location",
                )

        assertEquals(
            NavigationFaultCode.NoSuitableEdges,
            fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.FailClosedNavigationTruth,
            fault.disposition,
        )

        assertSame(
            NavigationRecoveryDecision.FailClosed,
            NavigationRecoveryPolicy()
                .decide(
                    fault =
                        fault,

                    retriesAlreadyAttempted =
                        0,
                ),
        )
    }

    @Test
    fun transportGetsExactlyOneBoundedRetryByDefault() {
        val fault =
            NavigationReliabilityClassifier
                .fromThrowable(
                    IOException(
                        "connection refused"
                    )
                )

        val policy =
            NavigationRecoveryPolicy()

        val first =
            policy.decide(
                fault =
                    fault,

                retriesAlreadyAttempted =
                    0,
            )

        assertTrue(
            first is
                NavigationRecoveryDecision.Retry
        )

        first as
            NavigationRecoveryDecision.Retry

        assertEquals(
            1,
            first.retryNumber,
        )

        assertEquals(
            400L,
            first.delayMs,
        )

        assertSame(
            NavigationRecoveryDecision.FailClosed,
            policy.decide(
                fault =
                    fault,

                retriesAlreadyAttempted =
                    1,
            ),
        )
    }

    @Test
    fun timeoutIsRetryableInfrastructure() {
        val fault =
            NavigationReliabilityClassifier
                .fromThrowable(
                    SocketTimeoutException(
                        "read timed out"
                    )
                )

        assertEquals(
            NavigationFaultCode.Timeout,
            fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.RetryableInfrastructure,
            fault.disposition,
        )
    }

    @Test
    fun invalidRouteResponseNeverRetries() {
        val fault =
            NavigationReliabilityClassifier
                .invalidRouteResponse(
                    "missing geometry"
                )

        assertEquals(
            NavigationFaultCode.InvalidResponse,
            fault.code,
        )

        assertSame(
            NavigationRecoveryDecision.FailClosed,
            NavigationRecoveryPolicy()
                .decide(
                    fault =
                        fault,

                    retriesAlreadyAttempted =
                        0,
                ),
        )
    }

    @Test
    fun typedFaultSurvivesThrowableClassification() {
        val fault =
            NavigationReliabilityClassifier
                .precisePermissionMissing()

        val exception =
            NavigationReliabilityException(
                fault
            )

        assertSame(
            fault,
            NavigationReliabilityClassifier
                .fromThrowable(
                    exception
                ),
        )
    }

    @Test
    fun serviceFiveHundredIsRetryableButBadRequestIsNot() {
        val unavailable =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        503,

                    responseBody =
                        "temporary outage",
                )

        val badRequest =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        400,

                    responseBody =
                        "invalid request",
                )

        assertEquals(
            NavigationFaultDisposition.RetryableInfrastructure,
            unavailable.disposition,
        )

        assertEquals(
            NavigationFaultDisposition.FailClosedNavigationTruth,
            badRequest.disposition,
        )
    }
}