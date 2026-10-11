package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationFaultBoundaryTest {

    @Test
    fun geocoderUnavailableIsTypedInfrastructureFault() {
        val fault =
            NavigationReliabilityClassifier
                .destinationSearchUnavailable(
                    "geocoder unavailable"
                )

        assertEquals(
            NavigationFaultCode.DestinationSearchUnavailable,
            fault.code,
        )

        assertEquals(
            NavigationFaultDomain.DestinationSearch,
            fault.domain,
        )

        assertEquals(
            NavigationFaultDisposition.RetryableInfrastructure,
            fault.disposition,
        )
    }

    @Test
    fun corruptPersistenceReadFallsBackWithoutFabricatingState() {
        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        val loaded =
            NavigationPersistenceReliability
                .loadOrFallback(
                    fallback =
                        "known-safe",

                    operationName =
                        "g5r5.corrupt-read",

                    onReliabilityEvent =
                        events::add,
                ) {
                    error(
                        "corrupt bytes"
                    )
                }

        assertEquals(
            "known-safe",
            loaded,
        )

        assertEquals(
            NavigationFaultCode.PersistenceUnavailable,
            events.first()
                .fault
                .code,
        )

        assertEquals(
            NavigationReliabilityEventKind.RecoveryExhausted,
            events.last()
                .kind,
        )
    }

    @Test
    fun persistenceWriteGetsExactlyOneRetry() {
        var attempts =
            0

        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        val saved =
            NavigationPersistenceReliability
                .saveWithSingleRetry(
                    operationName =
                        "g5r5.write-failure",

                    onReliabilityEvent =
                        events::add,
                ) {
                    attempts +=
                        1

                    false
                }

        assertFalse(
            saved
        )

        assertEquals(
            2,
            attempts,
        )

        assertEquals(
            1,
            events.count {
                it.kind ==
                    NavigationReliabilityEventKind.RecoveryScheduled
            },
        )
    }

    @Test
    fun mapFailureHasOneBoundedRetryOnly() {
        val fault =
            NavigationReliabilityClassifier
                .mapUnavailable(
                    "style unavailable"
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
    fun illegalRouteReplacementStateFailsClosed() {
        val error =
            runCatching {
                requireNavigatingRouteReplacementState(
                    NavigationSessionState.Preview
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
    fun navigatingRouteReplacementStateIsAccepted() {
        requireNavigatingRouteReplacementState(
            NavigationSessionState.Navigating
        )
    }
}