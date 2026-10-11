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
    fun structuredNoSuitableEdgesWinsWithoutRawLogParsing() {
        val fault =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        422,

                    responseBody =
                        """{"schemaVersion":1,"error":{"code":"no_suitable_edges","message":"graph miss","retryable":false}}""",
                )

        assertEquals(
            NavigationFaultCode.NoSuitableEdges,
            fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.FailClosedNavigationTruth,
            fault.disposition,
        )
    }

    @Test
    fun structuredBackendFailureIsBoundedRetryableInfrastructure() {
        val fault =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        502,

                    responseBody =
                        """{"schemaVersion":1,"error":{"code":"route_export_failed","message":"backend failed","retryable":true}}""",
                )

        assertEquals(
            NavigationFaultCode.ServiceUnavailable,
            fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.RetryableInfrastructure,
            fault.disposition,
        )
    }

    @Test
    fun structuredInvalidExportFailsClosed() {
        val fault =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        502,

                    responseBody =
                        """{"schemaVersion":1,"error":{"code":"invalid_exported_route","message":"invalid json","retryable":false}}""",
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
    fun unknownStructuredCodeCannotOverrideLocalRecoveryPolicy() {
        val fault =
            NavigationReliabilityClassifier
                .fromHttp(
                    responseCode =
                        400,

                    responseBody =
                        """{"schemaVersion":1,"error":{"code":"future_unknown","message":"unknown","retryable":true}}""",
                )

        assertEquals(
            NavigationFaultCode.InvalidRequest,
            fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.FailClosedNavigationTruth,
            fault.disposition,
        )
    }

    @Test
    fun persistenceReadFailureFallsBackAndReportsIncident() {
        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        val loaded =
            NavigationPersistenceReliability
                .loadOrFallback(
                    fallback =
                        "safe-fallback",

                    operationName =
                        "test.load",

                    onReliabilityEvent =
                        events::add,
                ) {
                    error(
                        "corrupt local state"
                    )
                }

        assertEquals(
            "safe-fallback",
            loaded,
        )

        assertEquals(
            NavigationFaultCode.PersistenceUnavailable,
            events.first().fault.code,
        )

        assertEquals(
            NavigationReliabilityEventKind.RecoveryExhausted,
            events.last().kind,
        )
    }

    @Test
    fun persistenceSaveRetriesExactlyOnceAndCanRecover() {
        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        var attempts =
            0

        val saved =
            NavigationPersistenceReliability
                .saveWithSingleRetry(
                    operationName =
                        "test.save",

                    onReliabilityEvent =
                        events::add,
                ) {
                    attempts +=
                        1

                    attempts ==
                        2
                }

        assertTrue(
            saved
        )

        assertEquals(
            2,
            attempts,
        )

        assertTrue(
            events.any {
                it.kind ==
                    NavigationReliabilityEventKind.RecoveryScheduled
            }
        )
    }

    @Test
    fun mapFailureGetsOneBoundedInfrastructureRetry() {
        val fault =
            NavigationReliabilityClassifier
                .mapUnavailable(
                    "style load failed"
                )

        val policy =
            NavigationRecoveryPolicy()

        assertTrue(
            policy.decide(
                fault =
                    fault,

                retriesAlreadyAttempted =
                    0,
            ) is
                NavigationRecoveryDecision.Retry
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
    fun nativeBoundaryViolationIsTypedAndFailsClosed() {
        val error =
            runCatching {
                requireNativeBoundary(
                    condition =
                        false,

                    detail =
                        "native route id mismatch",
                )
            }
                .exceptionOrNull()

        assertTrue(
            error is
                NavigationReliabilityException
        )

        error as
            NavigationReliabilityException

        assertEquals(
            NavigationFaultCode.NativeBoundaryRejected,
            error.fault.code,
        )

        assertEquals(
            NavigationFaultDisposition.FailClosedNavigationTruth,
            error.fault.disposition,
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