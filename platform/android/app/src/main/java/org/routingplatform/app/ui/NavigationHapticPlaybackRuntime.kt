package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.NavigationPreferences

internal enum class NavigationHapticSubmission {
    Performed,
    Rejected,
}

internal interface NavigationHapticSink :
    AutoCloseable {

    fun submit(
        cue:
            NavigationHapticCue,
    ): NavigationHapticSubmission

    fun cancel()
}

private data class NavigationHapticDeliveryKey(
    val cueKey:
        NavigationHapticCueKey,

    val fallbackManeuverEpoch:
        Long?,
)

private data class NavigationHapticFallbackSignature(
    val type:
        ManeuverType,

    val instruction:
        String,
)

internal class NavigationHapticPlaybackRuntime(
    private val sink:
        NavigationHapticSink,
) :
    AutoCloseable {

    private companion object {
        const val FALLBACK_DISTANCE_RESET_METERS =
            75.0
    }

    private var activeSessionId:
        String? =
        null

    private var activeRouteId:
        String? =
        null

    private val deliveredKeys =
        linkedSetOf<
            NavigationHapticDeliveryKey
        >()

    private var fallbackManeuverEpoch =
        0L

    private var fallbackManeuverSignature:
        NavigationHapticFallbackSignature? =
        null

    private var lastFallbackDistanceM:
        Double? =
        null

    fun present(
        snapshot:
            NavigationUiSnapshot,

        preferences:
            NavigationPreferences,
    ) {
        if (
            !preferences
                .hapticGuidanceEnabled ||
            snapshot.state !=
                NavigationSessionState
                    .Navigating ||
            snapshot.arrived
        ) {
            resetPlaybackSession()
            return
        }

        ensurePlaybackSession(
            sessionId =
                snapshot.sessionId,

            routeId =
                snapshot.routeId,
        )

        val fallbackEpoch =
            trackFallbackManeuver(
                snapshot
            )

        val cue =
            NavigationHapticPresentation
                .cue(
                    snapshot =
                        snapshot,

                    preferences =
                        preferences,
                )
                ?: return

        val deliveryKey =
            NavigationHapticDeliveryKey(
                cueKey =
                    cue.key,

                fallbackManeuverEpoch =
                    if (
                        cue.key
                            .beginShapeIndex ==
                            null ||
                        cue.key
                            .endShapeIndex ==
                            null
                    ) {
                        fallbackEpoch
                    } else {
                        null
                    },
            )

        if (
            deliveryKey in
                deliveredKeys
        ) {
            return
        }

        when (
            sink.submit(
                cue
            )
        ) {
            NavigationHapticSubmission
                .Performed ->
                deliveredKeys.add(
                    deliveryKey
                )

            NavigationHapticSubmission
                .Rejected ->
                Unit
        }
    }

    private fun trackFallbackManeuver(
        snapshot:
            NavigationUiSnapshot,
    ): Long? {
        val maneuver =
            snapshot.currentManeuver

        if (
            maneuver ==
                null ||
            (
                maneuver.beginShapeIndex !=
                    null &&
                    maneuver.endShapeIndex !=
                    null
            )
        ) {
            fallbackManeuverSignature =
                null

            lastFallbackDistanceM =
                null

            return null
        }

        val signature =
            NavigationHapticFallbackSignature(
                type =
                    maneuver.type,

                instruction =
                    maneuver
                        .instruction
                        .trim(),
            )

        val distanceMeters =
            snapshot
                .distanceToCurrentManeuverEndM

        val signatureChanged =
            fallbackManeuverSignature !=
                signature

        val distanceReset =
            !signatureChanged &&
                lastFallbackDistanceM
                    ?.let {
                            previousDistance ->

                        distanceMeters >
                            previousDistance +
                                FALLBACK_DISTANCE_RESET_METERS
                    }
                    ?: false

        if (
            signatureChanged ||
            distanceReset
        ) {
            fallbackManeuverEpoch +=
                1L
        }

        fallbackManeuverSignature =
            signature

        lastFallbackDistanceM =
            distanceMeters

        return fallbackManeuverEpoch
    }

    private fun ensurePlaybackSession(
        sessionId:
            String,

        routeId:
            String,
    ) {
        if (
            activeSessionId ==
                sessionId &&
            activeRouteId ==
                routeId
        ) {
            return
        }

        if (
            activeSessionId !=
                null
        ) {
            sink.cancel()
        }

        deliveredKeys.clear()
        resetFallbackIdentity()

        activeSessionId =
            sessionId

        activeRouteId =
            routeId
    }

    private fun resetPlaybackSession() {
        if (
            activeSessionId !=
                null
        ) {
            sink.cancel()
        }

        deliveredKeys.clear()
        resetFallbackIdentity()

        activeSessionId =
            null

        activeRouteId =
            null
    }

    private fun resetFallbackIdentity() {
        fallbackManeuverEpoch =
            0L

        fallbackManeuverSignature =
            null

        lastFallbackDistanceM =
            null
    }

    override fun close() {
        resetPlaybackSession()
        sink.close()
    }
}