package org.routingplatform.app.ui

import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.NavigationRouteFamily
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.NavigationPersonalityTone
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

/*
 * Voice-guidance presentation boundary.
 *
 * This policy consumes already-produced maneuver truth and effective voice
 * preferences. It does not alter route geometry, route progress, positioning,
 * rerouting, candidate selection, cost evaluation or any safety gate.
 *
 * Android text-to-speech output is intentionally outside this milestone.
 */
internal enum class NavigationVoiceCueStage {
    Early,
    Prepare,
    Now,
    CriticalRepeat,
}

internal data class NavigationVoiceCueKey(
    val sessionId:
        String,

    val routeId:
        String,

    val maneuverType:
        ManeuverType,

    val instruction:
        String,

    val beginShapeIndex:
        Int?,

    val endShapeIndex:
        Int?,

    val stage:
        NavigationVoiceCueStage,
)

internal data class NavigationVoiceCue(
    val key:
        NavigationVoiceCueKey,

    val text:
        String,

    val languageTag:
        String,

    val voiceId:
        String?,

    val speechRate:
        Double,

    val speechPitch:
        Double =
        1.0,
)

internal object NavigationVoicePresentation {
    fun routeStartCue(
        snapshot: NavigationUiSnapshot,
        routeFamily: NavigationRouteFamily,
        voice: VoicePreferences,
    ): NavigationVoiceCue? {
        if (!voice.enabled || snapshot.state != NavigationSessionState.Navigating) return null
        val german = voice.languageTag.startsWith("de", ignoreCase = true)
        val family = when (routeFamily) {
            NavigationRouteFamily.Fastest -> if (german) "schnellste Route" else "fastest route"
            NavigationRouteFamily.Shortest -> if (german) "kürzeste Route" else "shortest route"
            NavigationRouteFamily.ProfileOptimal -> if (german) "deinem Fahrprofil entsprechend ausgewogene Route" else "balanced route for your driving profile"
            NavigationRouteFamily.MajorRoads -> if (german) "Route über Hauptstraßen" else "route using major roads"
            NavigationRouteFamily.Comfort -> if (german) "Komfortroute" else "comfort route"
            NavigationRouteFamily.LowUrban -> if (german) "Route mit weniger Stadtverkehr laut Kartendaten" else "route with less urban driving according to map data"
            NavigationRouteFamily.LowCurvature -> if (german) "Route mit weniger Kurven laut Routendaten" else "route with fewer curves according to route data"
            NavigationRouteFamily.LowGradient -> if (german) "Route mit weniger Steigung laut Routendaten" else "route with fewer gradients according to route data"
            NavigationRouteFamily.LowTraffic -> if (german) "Route mit geringerem Verkehr laut Routendaten" else "route with less traffic according to route data"
            NavigationRouteFamily.Energy -> if (german) "energiesparende Route laut Routendaten" else "energy-efficient route according to route data"
            NavigationRouteFamily.Scenic -> if (german) "landschaftlich schöne Route laut Routendaten" else "scenic route according to route data"
            NavigationRouteFamily.Stable -> if (german) "stabile Route laut Routendaten" else "stable route according to route data"
        }
        val km = "%.1f".format(java.util.Locale.GERMANY, snapshot.routeDistanceM / 1000.0)
        val minutes = (snapshot.routeDurationS / 60.0).toInt().coerceAtLeast(1)
        val text = if (german) {
            "Route gewählt: $family. $km Kilometer, ungefähr $minutes Minuten. " +
                "Live-Verkehrsdaten sind nicht verbunden; Staus kann ich derzeit nicht verlässlich bestätigen."
        } else {
            "Route selected: $family. $km kilometers, about $minutes minutes. " +
                "Live traffic data is not connected, so I cannot reliably confirm congestion."
        }
        return NavigationVoiceCue(
            key = NavigationVoiceCueKey(
                sessionId = snapshot.sessionId,
                routeId = snapshot.routeId,
                maneuverType = ManeuverType.Continue,
                instruction = "route-start-summary:${routeFamily.name}",
                beginShapeIndex = null,
                endShapeIndex = null,
                stage = NavigationVoiceCueStage.Early,
            ),
            text = text,
            languageTag = voice.languageTag,
            voiceId = voice.voiceId,
            speechRate = voice.speechRate,
        )
    }

    fun experiencePackActivationCue(
        snapshot: NavigationUiSnapshot,
        voice: VoicePreferences,
        personality: NavigationPersonalityPreferences,
    ): NavigationVoiceCue? {
        if (!voice.enabled || snapshot.state != NavigationSessionState.Preview) return null
        val pack = ExperiencePackRuntimeResolver.resolvePackOverride(personality) ?: return null
        val german = voice.languageTag.startsWith("de", ignoreCase = true)
        val text = if (german) "Überraschungsstil ${pack.displayName} ist aktiv." else "Surprise style ${pack.displayName} is active."
        return NavigationVoiceCue(
            key = NavigationVoiceCueKey(
                sessionId = snapshot.sessionId,
                routeId = snapshot.routeId,
                maneuverType = ManeuverType.Continue,
                instruction = "experience-pack-active:${pack.packId}",
                beginShapeIndex = null,
                endShapeIndex = null,
                stage = NavigationVoiceCueStage.Early,
            ),
            text = text,
            languageTag = voice.languageTag,
            voiceId = voice.voiceId,
            speechRate = voice.speechRate,
        )
    }

    fun speedLimitCue(
        snapshot: NavigationUiSnapshot,
        warning: NavigationSpeedLimitWarning,
        voice: VoicePreferences,
    ): NavigationVoiceCue? {
        if (!voice.enabled || snapshot.state != NavigationSessionState.Navigating || snapshot.arrived || warning.routeId != snapshot.routeId) return null

        val stage = when (warning.kind) {
            NavigationSpeedLimitWarningKind.Approaching500m -> NavigationVoiceCueStage.Early
            NavigationSpeedLimitWarningKind.Approaching150m -> NavigationVoiceCueStage.Prepare
            NavigationSpeedLimitWarningKind.OverLimit -> NavigationVoiceCueStage.Now
        }
        val german = voice.languageTag.startsWith("de", ignoreCase = true)
        val text = when (warning.kind) {
            NavigationSpeedLimitWarningKind.Approaching500m -> if (german) "In 500 Metern gilt ein Tempolimit von ${warning.limitKmh} Stundenkilometern." else "Speed limit of ${warning.limitKmh} kilometers per hour in 500 meters."
            NavigationSpeedLimitWarningKind.Approaching150m -> if (german) "In 150 Metern gilt ein Tempolimit von ${warning.limitKmh} Stundenkilometern." else "Speed limit of ${warning.limitKmh} kilometers per hour in 150 meters."
            NavigationSpeedLimitWarningKind.OverLimit -> if (german) "Achtung. Das Tempolimit beträgt ${warning.limitKmh} Stundenkilometer." else "Caution. The speed limit is ${warning.limitKmh} kilometers per hour."
        }
        return NavigationVoiceCue(
            key = NavigationVoiceCueKey(
                sessionId = snapshot.sessionId, routeId = snapshot.routeId,
                maneuverType = ManeuverType.Continue,
                instruction = "speed-limit:${warning.deliveryId}",
                beginShapeIndex = warning.beginShapeIndex, endShapeIndex = warning.beginShapeIndex, stage = stage,
            ),
            text = text, languageTag = voice.languageTag, voiceId = voice.voiceId, speechRate = voice.speechRate,
        )
    }

    private const val NOW_DISTANCE_METERS =
        35.0

    private const val PREPARE_DISTANCE_METERS =
        180.0

    private const val EARLY_DISTANCE_METERS =
        500.0

    fun cue(
        snapshot:
            NavigationUiSnapshot,

        voice:
            VoicePreferences,

        navigationPreferences:
            NavigationPreferences =
            NavigationPreferences(),

        personality:
            NavigationPersonalityPreferences =
            NavigationPersonalityPreferences(),
    ): NavigationVoiceCue? {
        if (
            !voice.enabled ||
            snapshot.state !=
                NavigationSessionState.Navigating ||
            snapshot.arrived
        ) {
            return null
        }

        val maneuver =
            snapshot.currentManeuver
                ?: return null

        val instruction =
            maneuver.guidanceInstruction
                .trim()

        if (
            instruction.isEmpty()
        ) {
            return null
        }

        val repeatCritical =
            NavigationCriticalGuidanceRepeatPolicy
                .shouldRepeat(
                    enabled =
                        navigationPreferences
                            .repeatCriticalInstructions,

                    maneuverType =
                        maneuver.type,

                    distanceMeters =
                        snapshot
                            .distanceToCurrentManeuverEndM,
                )

        val stage =
            if (
                repeatCritical
            ) {
                NavigationVoiceCueStage
                    .CriticalRepeat
            } else {
                cueStage(
                    distanceMeters =
                        snapshot
                            .distanceToCurrentManeuverEndM,

                    verbosity =
                        voice.verbosity,
                )
                    ?: return null
            }

        val activePack =
            ExperiencePackRuntimeResolver
                .resolvePackOverride(personality)

        // Surprise themes affect ordinary spoken cues. Maneuver truth remains
        // intact and urgent/critical repeats are deliberately kept neutral.
        val themeLeadIn = if (stage == NavigationVoiceCueStage.CriticalRepeat) {
            null
        } else {
            activePack?.let { pack -> themeLeadIn(pack.tone, stage, voice.languageTag) }
        }
        val speechPitch = if (activePack?.tone == NavigationPersonalityTone.Futuristic && themeLeadIn != null) 1.08 else 1.0
        val speechText = listOfNotNull(themeLeadIn, instruction).joinToString(" ")

        return NavigationVoiceCue(
            key =
                NavigationVoiceCueKey(
                    sessionId =
                        snapshot.sessionId,

                    routeId =
                        snapshot.routeId,

                    maneuverType =
                        maneuver.type,

                    instruction =
                        instruction,

                    beginShapeIndex =
                        maneuver.beginShapeIndex,

                    endShapeIndex =
                        maneuver.endShapeIndex,

                    stage =
                        stage,
                ),

            text =
                speechText,

            languageTag =
                voice.languageTag,

            voiceId =
                voice.voiceId,

            speechRate =
                voice.speechRate,

            speechPitch =
                speechPitch,
        )
    }

    private fun themeLeadIn(
        tone: NavigationPersonalityTone,
        stage: NavigationVoiceCueStage,
        languageTag: String,
    ): String? {
        val german = languageTag.startsWith("de", ignoreCase = true)
        return when (tone) {
            NavigationPersonalityTone.Neutral -> null
            NavigationPersonalityTone.Calm -> if (german) "Ganz ruhig:" else "Easy now:"
            NavigationPersonalityTone.Warm -> if (german) "Gern für dich:" else "For you:"
            NavigationPersonalityTone.DryHumor -> if (german) "Kleine Kurskorrektur:" else "A little course correction:"
            NavigationPersonalityTone.Futuristic -> when (stage) {
                NavigationVoiceCueStage.Early -> if (german) "Sternenkarte voraus:" else "Star chart ahead:"
                NavigationVoiceCueStage.Prepare -> if (german) "Sprungfenster voraus:" else "Jump window ahead:"
                NavigationVoiceCueStage.Now -> if (german) "Jetzt, Pilot:" else "Now, pilot:"
                NavigationVoiceCueStage.CriticalRepeat -> null
            }
            NavigationPersonalityTone.CoPilot -> if (german) "Ich bin dabei:" else "I'm with you:"
        }
    }

    private fun cueStage(
        distanceMeters:
            Double,

        verbosity:
            VoiceGuidanceVerbosity,
    ): NavigationVoiceCueStage? =
        when {
            distanceMeters <=
                NOW_DISTANCE_METERS ->
                NavigationVoiceCueStage.Now

            verbosity ==
                VoiceGuidanceVerbosity.Minimal &&
                distanceMeters <=
                    PREPARE_DISTANCE_METERS ->
                null

            verbosity ==
                VoiceGuidanceVerbosity.Minimal &&
                distanceMeters <=
                    EARLY_DISTANCE_METERS ->
                NavigationVoiceCueStage.Early

            distanceMeters <=
                PREPARE_DISTANCE_METERS ->
                NavigationVoiceCueStage.Prepare

            distanceMeters <=
                EARLY_DISTANCE_METERS ->
                NavigationVoiceCueStage.Early

            else ->
                null
        }
}
