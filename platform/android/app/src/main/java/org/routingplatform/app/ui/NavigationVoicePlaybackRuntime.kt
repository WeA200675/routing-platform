package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoicePreferences

/*
 * Pure playback coordinator.
 *
 * It consumes G6.5 presentation cues and owns only playback deduplication.
 * It cannot alter routing, route progress, positioning, rerouting or safety state.
 *
 * Product JNI snapshots normally carry maneuver shape indices. When a fallback
 * source cannot provide them, the coordinator derives a local playback-only
 * maneuver epoch from instruction/type changes or a substantial distance reset.
 * This prevents two distinct but identically-worded fallback maneuvers from
 * collapsing onto the same delivery key.
 */
internal enum class NavigationSpeechSubmission {
    Queued,
    Spoken,
    Rejected,
}

internal interface NavigationVoiceSpeaker : AutoCloseable {
    fun submit(cue: NavigationVoiceCue): NavigationSpeechSubmission
    fun clearPending()
    fun stop()
}

private data class NavigationVoiceDeliveryKey(
    val cueKey: NavigationVoiceCueKey,
    val fallbackManeuverEpoch: Long?,
)

private data class NavigationVoiceManeuverProgressKey(
    val sessionId: String,
    val routeId: String,
    val maneuverType: ManeuverType,
    val instruction: String,
    val beginShapeIndex: Int?,
    val endShapeIndex: Int?,
    val fallbackManeuverEpoch: Long?,
)

private fun NavigationVoiceCueStage.progressRank(): Int =
    when (this) {
        NavigationVoiceCueStage.Early -> 0
        NavigationVoiceCueStage.Prepare -> 1
        NavigationVoiceCueStage.Now -> 2
        NavigationVoiceCueStage.CriticalRepeat -> 3
    }

private data class FallbackManeuverSignature(
    val type: ManeuverType,
    val instruction: String,
)

internal class NavigationVoicePlaybackRuntime(
    private val speaker: NavigationVoiceSpeaker,
) : AutoCloseable {
    private companion object {
        /*
         * Large enough to ignore ordinary distance jitter, while still
         * detecting the normal jump from a completed maneuver to the next one.
         * This value affects playback identity only; it never affects routing.
         */
        const val FALLBACK_DISTANCE_RESET_METERS =
            75.0
    }

    private var activeSessionId: String? = null
    private var activeRouteId: String? = null

    private val deliveredKeys =
        linkedSetOf<NavigationVoiceDeliveryKey>()

    private val highestDeliveredStageByManeuver =
        mutableMapOf<NavigationVoiceManeuverProgressKey, Int>()

    private var fallbackManeuverEpoch =
        0L

    private var fallbackManeuverSignature:
        FallbackManeuverSignature? =
        null

    private var lastFallbackDistanceM:
        Double? =
        null

    fun present(
        snapshot: NavigationUiSnapshot,
        voice: VoicePreferences,
        navigationPreferences: NavigationPreferences =
            NavigationPreferences(),
        personality: NavigationPersonalityPreferences =
            NavigationPersonalityPreferences(),
    ) {
        if (
            !voice.enabled ||
            snapshot.state != NavigationSessionState.Navigating ||
            snapshot.arrived
        ) {
            resetPlaybackSession()
            return
        }

        ensurePlaybackSession(
            sessionId = snapshot.sessionId,
            routeId = snapshot.routeId,
        )

        val fallbackEpoch =
            trackFallbackManeuver(
                snapshot
            )

        val cue =
            NavigationVoicePresentation.cue(
                snapshot = snapshot,
                voice = voice,
                navigationPreferences =
                    navigationPreferences,
                personality =
                    personality,
            )

        if (cue == null) {
            speaker.clearPending()
            return
        }

        val deliveryKey =
            NavigationVoiceDeliveryKey(
                cueKey = cue.key,
                fallbackManeuverEpoch =
                    if (
                        cue.key.beginShapeIndex == null ||
                        cue.key.endShapeIndex == null
                    ) {
                        fallbackEpoch
                    } else {
                        null
                    },
            )

        if (deliveryKey in deliveredKeys) {
            return
        }

        val cueKey = cue.key
        val maneuverProgressKey =
            NavigationVoiceManeuverProgressKey(
                sessionId = cueKey.sessionId,
                routeId = cueKey.routeId,
                maneuverType = cueKey.maneuverType,
                instruction = cueKey.instruction,
                beginShapeIndex = cueKey.beginShapeIndex,
                endShapeIndex = cueKey.endShapeIndex,
                fallbackManeuverEpoch =
                    if (
                        cueKey.beginShapeIndex == null ||
                        cueKey.endShapeIndex == null
                    ) {
                        fallbackEpoch
                    } else {
                        null
                    },
            )
        val stageRank = cueKey.stage.progressRank()
        if (
            highestDeliveredStageByManeuver[maneuverProgressKey]
                ?.let { stageRank < it }
                ?: false
        ) {
            // Distance can wobble backwards; never replay an earlier cue
            // after the driver has already received a later-stage instruction.
            return
        }

        when (speaker.submit(cue)) {
            NavigationSpeechSubmission.Queued,
            NavigationSpeechSubmission.Spoken -> {
                deliveredKeys.add(deliveryKey)
                highestDeliveredStageByManeuver[maneuverProgressKey] =
                    maxOf(
                        stageRank,
                        highestDeliveredStageByManeuver[maneuverProgressKey] ?: stageRank,
                    )
            }

            NavigationSpeechSubmission.Rejected ->
                Unit
        }
    }

    private fun trackFallbackManeuver(
        snapshot: NavigationUiSnapshot,
    ): Long? {
        val maneuver =
            snapshot.currentManeuver

        if (
            maneuver == null ||
            (
                maneuver.beginShapeIndex != null &&
                    maneuver.endShapeIndex != null
            )
        ) {
            fallbackManeuverSignature =
                null

            lastFallbackDistanceM =
                null

            return null
        }

        val signature =
            FallbackManeuverSignature(
                type = maneuver.type,
                instruction =
                    maneuver.instruction.trim(),
            )

        val distanceMeters =
            snapshot.distanceToCurrentManeuverEndM

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
        sessionId: String,
        routeId: String,
    ) {
        if (
            activeSessionId == sessionId &&
            activeRouteId == routeId
        ) {
            return
        }

        if (activeSessionId != null) {
            speaker.stop()
        }

        speaker.clearPending()
        deliveredKeys.clear()
        highestDeliveredStageByManeuver.clear()
        resetFallbackIdentity()

        activeSessionId = sessionId
        activeRouteId = routeId
    }

    private fun resetPlaybackSession() {
        speaker.clearPending()

        if (activeSessionId != null) {
            speaker.stop()
        }

        deliveredKeys.clear()
        highestDeliveredStageByManeuver.clear()
        resetFallbackIdentity()

        activeSessionId = null
        activeRouteId = null
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
        speaker.close()
    }
}