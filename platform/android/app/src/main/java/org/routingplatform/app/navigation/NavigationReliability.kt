package org.routingplatform.app.navigation

import java.io.IOException
import java.net.SocketTimeoutException

/*
 * Reliability rule:
 *
 * Self-heal infrastructure; fail closed on navigation truth.
 *
 * Infrastructure faults may receive a small, bounded retry budget.
 * Route/position/session truth is never fabricated, guessed or advanced
 * to make recovery appear successful.
 */
enum class NavigationFaultDomain {
    Transport,
    RoutingBackend,
    RouteContract,
    PlanningLocation,
    Permission,
    Persistence,
    MapPresentation,
    NativeBoundary,
    Unknown,
}

enum class NavigationFaultDisposition {
    RetryableInfrastructure,
    FailClosedNavigationTruth,
    NonRetryableConfiguration,
}

enum class NavigationFaultCode {
    TransportUnavailable,
    Timeout,
    ServiceUnavailable,
    ServiceRejected,
    NoSuitableEdges,
    InvalidRequest,
    InvalidResponse,
    PermissionMissing,
    PlanningLocationUnavailable,
    PlanningLocationSourceUnavailable,
    PersistenceUnavailable,
    MapUnavailable,
    NativeBoundaryRejected,
    Unknown,
}

data class NavigationFault(
    val code:
        NavigationFaultCode,

    val domain:
        NavigationFaultDomain,

    val disposition:
        NavigationFaultDisposition,

    val userMessage:
        String,

    val technicalMessage:
        String,
) {
    init {
        require(
            userMessage.isNotBlank()
        )

        require(
            technicalMessage.isNotBlank()
        )
    }
}

class NavigationReliabilityException(
    val fault:
        NavigationFault,

    cause:
        Throwable? =
        null,
) :
    IllegalStateException(
        fault.userMessage,
        cause,
    )

sealed class NavigationRecoveryDecision {
    data class Retry(
        val delayMs:
            Long,

        val retryNumber:
            Int,
    ) :
        NavigationRecoveryDecision()

    object FailClosed :
        NavigationRecoveryDecision()
}

data class NavigationRecoveryPolicy(
    val maxInfrastructureRetries:
        Int =
        1,

    val retryCooldownMs:
        Long =
        400L,
) {
    init {
        require(
            maxInfrastructureRetries in
                0..3
        )

        require(
            retryCooldownMs in
                0L..5_000L
        )
    }

    fun decide(
        fault:
            NavigationFault,

        retriesAlreadyAttempted:
            Int,
    ): NavigationRecoveryDecision {

        require(
            retriesAlreadyAttempted >=
                0
        )

        if (
            fault.disposition !=
                NavigationFaultDisposition.RetryableInfrastructure
        ) {
            return NavigationRecoveryDecision.FailClosed
        }

        if (
            retriesAlreadyAttempted >=
                maxInfrastructureRetries
        ) {
            return NavigationRecoveryDecision.FailClosed
        }

        return NavigationRecoveryDecision.Retry(
            delayMs =
                retryCooldownMs,

            retryNumber =
                retriesAlreadyAttempted +
                    1,
        )
    }
}

enum class NavigationReliabilityEventKind {
    FaultObserved,
    RecoveryScheduled,
    RecoveryExhausted,
}

data class NavigationReliabilityEvent(
    val kind:
        NavigationReliabilityEventKind,

    val fault:
        NavigationFault,

    val retryNumber:
        Int,

    val delayMs:
        Long,
)

internal object NavigationReliabilityClassifier {

    fun fromHttp(
        responseCode:
            Int,

        responseBody:
            String,
    ): NavigationFault {

        val technical =
            (
                "HTTP $responseCode: " +
                    responseBody
                        .take(
                            MAX_TECHNICAL_DETAIL_CHARS
                        )
            )
                .trim()

        if (
            responseBody.contains(
                "No suitable edges near location",
                ignoreCase =
                    true,
            )
        ) {
            return NavigationFault(
                code =
                    NavigationFaultCode.NoSuitableEdges,

                domain =
                    NavigationFaultDomain.RoutingBackend,

                disposition =
                    NavigationFaultDisposition.FailClosedNavigationTruth,

                userMessage =
                    "Für den gewählten Punkt wurde keine geeignete befahrbare Straße gefunden.",

                technicalMessage =
                    technical,
            )
        }

        return when (
            responseCode
        ) {
            400 ->
                NavigationFault(
                    code =
                        NavigationFaultCode.InvalidRequest,

                    domain =
                        NavigationFaultDomain.RoutingBackend,

                    disposition =
                        NavigationFaultDisposition.FailClosedNavigationTruth,

                    userMessage =
                        "Die Routenanfrage wurde vom Routing-Dienst abgelehnt.",

                    technicalMessage =
                        technical,
                )

            408,
            504 ->
                timeout(
                    technical
                )

            429,
            in 500..599 ->
                NavigationFault(
                    code =
                        NavigationFaultCode.ServiceUnavailable,

                    domain =
                        NavigationFaultDomain.RoutingBackend,

                    disposition =
                        NavigationFaultDisposition.RetryableInfrastructure,

                    userMessage =
                        "Der Routing-Dienst ist vorübergehend nicht verfügbar.",

                    technicalMessage =
                        technical,
                )

            else ->
                NavigationFault(
                    code =
                        NavigationFaultCode.ServiceRejected,

                    domain =
                        NavigationFaultDomain.RoutingBackend,

                    disposition =
                        NavigationFaultDisposition.NonRetryableConfiguration,

                    userMessage =
                        "Der Routing-Dienst hat die Anfrage nicht akzeptiert.",

                    technicalMessage =
                        technical,
                )
        }
    }

    fun fromThrowable(
        error:
            Throwable,
    ): NavigationFault {

        if (
            error is
                NavigationReliabilityException
        ) {
            return error.fault
        }

        return when (
            error
        ) {
            is SocketTimeoutException ->
                timeout(
                    error.message
                        ?: "Socket timeout"
                )

            is IOException ->
                NavigationFault(
                    code =
                        NavigationFaultCode.TransportUnavailable,

                    domain =
                        NavigationFaultDomain.Transport,

                    disposition =
                        NavigationFaultDisposition.RetryableInfrastructure,

                    userMessage =
                        "Der Routing-Dienst ist derzeit nicht erreichbar.",

                    technicalMessage =
                        error.message
                            ?: error.javaClass.name,
                )

            is IllegalArgumentException ->
                NavigationFault(
                    code =
                        NavigationFaultCode.InvalidRequest,

                    domain =
                        NavigationFaultDomain.RouteContract,

                    disposition =
                        NavigationFaultDisposition.FailClosedNavigationTruth,

                    userMessage =
                        "Die Routenanfrage ist ungültig.",

                    technicalMessage =
                        error.message
                            ?: error.javaClass.name,
                )

            else ->
                NavigationFault(
                    code =
                        NavigationFaultCode.Unknown,

                    domain =
                        NavigationFaultDomain.Unknown,

                    disposition =
                        NavigationFaultDisposition.FailClosedNavigationTruth,

                    userMessage =
                        "Die Navigation konnte die Anfrage nicht sicher abschließen.",

                    technicalMessage =
                        error.message
                            ?: error.javaClass.name,
                )
        }
    }

    fun invalidRouteResponse(
        detail:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.InvalidResponse,

            domain =
                NavigationFaultDomain.RouteContract,

            disposition =
                NavigationFaultDisposition.FailClosedNavigationTruth,

            userMessage =
                "Der Routing-Dienst hat eine ungültige Antwort geliefert.",

            technicalMessage =
                detail.take(
                    MAX_TECHNICAL_DETAIL_CHARS
                ),
        )

    fun precisePermissionMissing():
        NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.PermissionMissing,

            domain =
                NavigationFaultDomain.Permission,

            disposition =
                NavigationFaultDisposition.FailClosedNavigationTruth,

            userMessage =
                "Präzise Position ist für den Routenstart erforderlich.",

            technicalMessage =
                "ACCESS_FINE_LOCATION is not granted for route planning.",
        )

    fun planningLocationUnavailable():
        NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.PlanningLocationUnavailable,

            domain =
                NavigationFaultDomain.PlanningLocation,

            disposition =
                NavigationFaultDisposition.FailClosedNavigationTruth,

            userMessage =
                "Keine ausreichend genaue aktuelle Position erhalten.",

            technicalMessage =
                "Planning location timed out before a fresh precise sample was accepted.",
        )

    fun planningLocationSourceUnavailable():
        NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.PlanningLocationSourceUnavailable,

            domain =
                NavigationFaultDomain.PlanningLocation,

            disposition =
                NavigationFaultDisposition.RetryableInfrastructure,

            userMessage =
                "Die Positionsquelle konnte für die Routenplanung nicht gestartet werden.",

            technicalMessage =
                "AndroidLocationSource.start() returned false for route planning.",
        )

    private fun timeout(
        technical:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.Timeout,

            domain =
                NavigationFaultDomain.Transport,

            disposition =
                NavigationFaultDisposition.RetryableInfrastructure,

            userMessage =
                "Der Routing-Dienst hat nicht rechtzeitig geantwortet.",

            technicalMessage =
                technical.take(
                    MAX_TECHNICAL_DETAIL_CHARS
                ),
        )
}

private const val MAX_TECHNICAL_DETAIL_CHARS =
    1_000