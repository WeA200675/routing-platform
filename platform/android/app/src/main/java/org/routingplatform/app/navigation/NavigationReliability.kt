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
    DestinationSearch,
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
    DestinationSearchUnavailable,
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

        val serviceError =
            parseServiceError(
                responseBody
            )

        val technical =
            if (
                serviceError !=
                    null
            ) {
                (
                    "HTTP $responseCode " +
                        "serviceCode=" +
                        serviceError.code +
                        " message=" +
                        serviceError.message
                )
                    .take(
                        MAX_TECHNICAL_DETAIL_CHARS
                    )
            } else {
                (
                    "HTTP $responseCode " +
                        "unstructured=" +
                        responseBody.take(
                            MAX_UNSTRUCTURED_DETAIL_CHARS
                        )
                )
            }

        val structuredFault =
            serviceError
                ?.let {
                    structuredServiceFault(
                        error =
                            it,

                        technical =
                            technical,
                    )
                }

        if (
            structuredFault !=
                null
        ) {
            return structuredFault
        }

        /*
         * Compatibility fallback for an older development service.
         * Structured service codes above are authoritative.
         */
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

    fun destinationSearchUnavailable(
        detail:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.DestinationSearchUnavailable,

            domain =
                NavigationFaultDomain.DestinationSearch,

            disposition =
                NavigationFaultDisposition.RetryableInfrastructure,

            userMessage =
                "Die Zielsuche ist vorübergehend nicht verfügbar.",

            technicalMessage =
                detail
                    .take(
                        MAX_TECHNICAL_DETAIL_CHARS
                    )
                    .ifBlank {
                        "Destination search unavailable."
                    },
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

    fun persistenceUnavailable(
        detail:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.PersistenceUnavailable,

            domain =
                NavigationFaultDomain.Persistence,

            disposition =
                NavigationFaultDisposition.RetryableInfrastructure,

            userMessage =
                "Lokale Einstellungen konnten nicht zuverlässig gelesen oder gespeichert werden.",

            technicalMessage =
                detail.take(
                    MAX_TECHNICAL_DETAIL_CHARS
                ),
        )

    fun mapUnavailable(
        detail:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.MapUnavailable,

            domain =
                NavigationFaultDomain.MapPresentation,

            disposition =
                NavigationFaultDisposition.RetryableInfrastructure,

            userMessage =
                "Die Kartenansicht ist vorübergehend nicht verfügbar.",

            technicalMessage =
                detail.take(
                    MAX_TECHNICAL_DETAIL_CHARS
                ),
        )

    fun nativeBoundaryRejected(
        detail:
            String,
    ): NavigationFault =
        NavigationFault(
            code =
                NavigationFaultCode.NativeBoundaryRejected,

            domain =
                NavigationFaultDomain.NativeBoundary,

            disposition =
                NavigationFaultDisposition.FailClosedNavigationTruth,

            userMessage =
                "Die Navigation hat einen ungültigen nativen Zustand abgelehnt.",

            technicalMessage =
                detail.take(
                    MAX_TECHNICAL_DETAIL_CHARS
                ),
        )

    private fun structuredServiceFault(
        error:
            NavigationServiceErrorPayload,

        technical:
            String,
    ): NavigationFault? =
        when (
            error.code
        ) {
            "no_suitable_edges" ->
                NavigationFault(
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

            "invalid_request" ->
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

            "routing_timeout" ->
                timeout(
                    technical
                )

            "route_export_failed",
            "backend_failure",
            "backend_not_ready" ->
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

            "invalid_exported_route",
            "response_too_large" ->
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
                        technical,
                )

            "development_header_required",
            "not_found" ->
                NavigationFault(
                    code =
                        NavigationFaultCode.ServiceRejected,

                    domain =
                        NavigationFaultDomain.RoutingBackend,

                    disposition =
                        NavigationFaultDisposition.NonRetryableConfiguration,

                    userMessage =
                        "Der Routing-Dienst ist nicht korrekt konfiguriert.",

                    technicalMessage =
                        technical,
                )

            else ->
                null
        }

    private fun parseServiceError(
        responseBody:
            String,
    ): NavigationServiceErrorPayload? {

        /*
         * Keep the reliability classifier pure Kotlin.
         *
         * Android's org.json implementation is not a trustworthy JVM
         * unit-test dependency: local Android unit tests use framework
         * stubs unless an explicit implementation is supplied.
         *
         * This parser intentionally understands only our small,
         * versioned error envelope. Unknown/malformed schemas return
         * null and therefore fall back to the conservative HTTP policy.
         */
        val bounded =
            responseBody.take(
                MAX_SERVICE_ENVELOPE_CHARS
            )

        val schemaVersion =
            SERVICE_SCHEMA_REGEX
                .find(
                    bounded
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?.toIntOrNull()
                ?: return null

        if (
            schemaVersion !=
                SERVICE_ERROR_SCHEMA_VERSION
        ) {
            return null
        }

        val errorObject =
            SERVICE_ERROR_OBJECT_REGEX
                .find(
                    bounded
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?: return null

        val encodedCode =
            SERVICE_CODE_REGEX
                .find(
                    errorObject
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                ?: return null

        val code =
            decodeJsonString(
                encodedCode
            )
                ?.trim()
                .orEmpty()

        if (
            code.isEmpty()
        ) {
            return null
        }

        val encodedMessage =
            SERVICE_MESSAGE_REGEX
                .find(
                    errorObject
                )
                ?.groupValues
                ?.getOrNull(
                    1
                )
                .orEmpty()

        val message =
            decodeJsonString(
                encodedMessage
            )
                ?.trim()
                ?.take(
                    MAX_SERVICE_MESSAGE_CHARS
                )
                .orEmpty()

        return NavigationServiceErrorPayload(
            code =
                code,

            message =
                message,
        )
    }

    private fun decodeJsonString(
        value:
            String,
    ): String? {

        val output =
            StringBuilder(
                value.length
            )

        var index =
            0

        while (
            index <
                value.length
        ) {
            val current =
                value[
                    index
                ]

            if (
                current !=
                    '\\'
            ) {
                output.append(
                    current
                )

                index +=
                    1

                continue
            }

            if (
                index + 1 >=
                    value.length
            ) {
                return null
            }

            val escaped =
                value[
                    index + 1
                ]

            when (
                escaped
            ) {
                '"',
                '\\',
                '/' -> {
                    output.append(
                        escaped
                    )

                    index +=
                        2
                }

                'b' -> {
                    output.append(
                        '\b'
                    )

                    index +=
                        2
                }

                'f' -> {
                    output.append(
                        '\u000C'
                    )

                    index +=
                        2
                }

                'n' -> {
                    output.append(
                        '\n'
                    )

                    index +=
                        2
                }

                'r' -> {
                    output.append(
                        '\r'
                    )

                    index +=
                        2
                }

                't' -> {
                    output.append(
                        '\t'
                    )

                    index +=
                        2
                }

                'u' -> {
                    if (
                        index + 6 >
                            value.length
                    ) {
                        return null
                    }

                    val codeUnit =
                        value
                            .substring(
                                index + 2,
                                index + 6,
                            )
                            .toIntOrNull(
                                16
                            )
                            ?: return null

                    output.append(
                        codeUnit.toChar()
                    )

                    index +=
                        6
                }

                else ->
                    return null
            }
        }

        return output.toString()
    }

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

internal object NavigationPersistenceReliability {

    fun <T> loadOrFallback(
        fallback:
            T,

        operationName:
            String,

        onReliabilityEvent:
            (NavigationReliabilityEvent) -> Unit,

        operation:
            () -> T,
    ): T {
        return try {
            operation()
        } catch (
            error:
                Exception
        ) {
            val fault =
                NavigationReliabilityClassifier
                    .persistenceUnavailable(
                        "$operationName failed: " +
                            error.javaClass.name
                    )

            onReliabilityEvent(
                NavigationReliabilityEvent(
                    kind =
                        NavigationReliabilityEventKind.FaultObserved,

                    fault =
                        fault,

                    retryNumber =
                        0,

                    delayMs =
                        0L,
                )
            )

            /*
             * Reads fail over immediately to the caller-provided
             * safe fallback. We do not repeatedly decode corrupted
             * local state.
             */
            onReliabilityEvent(
                NavigationReliabilityEvent(
                    kind =
                        NavigationReliabilityEventKind.RecoveryExhausted,

                    fault =
                        fault,

                    retryNumber =
                        0,

                    delayMs =
                        0L,
                )
            )

            fallback
        }
    }

    fun saveWithSingleRetry(
        operationName:
            String,

        onReliabilityEvent:
            (NavigationReliabilityEvent) -> Unit,

        operation:
            () -> Boolean,
    ): Boolean {

        var retriesAlreadyAttempted =
            0

        while (true) {
            val attempt =
                try {
                    operation()
                } catch (
                    error:
                        Exception
                ) {
                    false
                }

            if (
                attempt
            ) {
                return true
            }

            val fault =
                NavigationReliabilityClassifier
                    .persistenceUnavailable(
                        "$operationName failed to commit."
                    )

            onReliabilityEvent(
                NavigationReliabilityEvent(
                    kind =
                        NavigationReliabilityEventKind.FaultObserved,

                    fault =
                        fault,

                    retryNumber =
                        retriesAlreadyAttempted,

                    delayMs =
                        0L,
                )
            )

            if (
                retriesAlreadyAttempted >=
                    MAX_PERSISTENCE_SAVE_RETRIES
            ) {
                onReliabilityEvent(
                    NavigationReliabilityEvent(
                        kind =
                            NavigationReliabilityEventKind.RecoveryExhausted,

                        fault =
                            fault,

                        retryNumber =
                            retriesAlreadyAttempted,

                        delayMs =
                            0L,
                    )
                )

                return false
            }

            retriesAlreadyAttempted +=
                1

            onReliabilityEvent(
                NavigationReliabilityEvent(
                    kind =
                        NavigationReliabilityEventKind.RecoveryScheduled,

                    fault =
                        fault,

                    retryNumber =
                        retriesAlreadyAttempted,

                    /*
                     * SharedPreferences.commit() is synchronous and
                     * idempotent for this write. Retry once without
                     * sleeping on the UI thread.
                     */
                    delayMs =
                        0L,
                )
            )
        }
    }
}

internal fun requireNativeBoundary(
    condition:
        Boolean,

    detail:
        String,
) {
    if (
        !condition
    ) {
        throw NavigationReliabilityException(
            NavigationReliabilityClassifier
                .nativeBoundaryRejected(
                    detail
                )
        )
    }
}

internal fun requireNavigatingRouteReplacementState(
    state:
        NavigationSessionState,
) {
    requireNativeBoundary(
        condition =
            state ==
                NavigationSessionState.Navigating,

        detail =
            "Active route replacement requires Navigating state.",
    )
}

private data class NavigationServiceErrorPayload(
    val code:
        String,

    val message:
        String,
)

private const val MAX_PERSISTENCE_SAVE_RETRIES =
    1

private const val MAX_TECHNICAL_DETAIL_CHARS =
    1_000

private const val MAX_UNSTRUCTURED_DETAIL_CHARS =
    300

private const val MAX_SERVICE_MESSAGE_CHARS =
    300

private const val SERVICE_ERROR_SCHEMA_VERSION =
    1

private const val MAX_SERVICE_ENVELOPE_CHARS =
    4_096

private val SERVICE_SCHEMA_REGEX =
    Regex(
        "\"schemaVersion\"\\s*:\\s*(\\d+)"
    )

private val SERVICE_ERROR_OBJECT_REGEX =
    Regex(
        "\"error\"\\s*:\\s*\\{((?:\"(?:\\\\.|[^\"\\\\])*\"|[^{}])*)\\}"
    )

private val SERVICE_CODE_REGEX =
    Regex(
        "\"code\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    )

private val SERVICE_MESSAGE_REGEX =
    Regex(
        "\"message\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    )