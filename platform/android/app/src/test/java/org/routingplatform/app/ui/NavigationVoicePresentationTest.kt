package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationManeuver
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.profile.ExperiencePackCatalog
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.ExperiencePackSelectionSource
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

class NavigationVoicePresentationTest {

    @Test
    fun disabledVoiceProducesNoCue() {
        assertNull(
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                20.0
                        ),

                    voice =
                        voice(
                            enabled =
                                false
                        ),
                )
        )
    }

    @Test
    fun previewAndArrivedStatesProduceNoCue() {
        assertNull(
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            state =
                                NavigationSessionState.Preview,

                            distanceMeters =
                                20.0,
                        ),

                    voice =
                        voice(),
                )
        )

        assertNull(
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            state =
                                NavigationSessionState.Arrived,

                            arrived =
                                true,

                            distanceMeters =
                                0.0,
                        ),

                    voice =
                        voice(),
                )
        )
    }

    @Test
    fun minimalVerbosityOnlyEmitsNowCue() {
        assertNull(
            cue(
                distanceMeters =
                    180.0,

                verbosity =
                    VoiceGuidanceVerbosity.Minimal,
            )
        )

        assertEquals(
            NavigationVoiceCueStage.Now,
            cue(
                distanceMeters =
                    35.0,

                verbosity =
                    VoiceGuidanceVerbosity.Minimal,
            )
                ?.key
                ?.stage,
        )
    }

    @Test
    fun standardVerbosityEmitsPrepareAndNowCues() {
        assertNull(
            cue(
                distanceMeters =
                    500.0,

                verbosity =
                    VoiceGuidanceVerbosity.Standard,
            )
        )

        assertEquals(
            NavigationVoiceCueStage.Prepare,
            cue(
                distanceMeters =
                    180.0,

                verbosity =
                    VoiceGuidanceVerbosity.Standard,
            )
                ?.key
                ?.stage,
        )

        assertEquals(
            NavigationVoiceCueStage.Now,
            cue(
                distanceMeters =
                    35.0,

                verbosity =
                    VoiceGuidanceVerbosity.Standard,
            )
                ?.key
                ?.stage,
        )
    }

    @Test
    fun detailedVerbosityEmitsEarlyPrepareAndNowCues() {
        assertEquals(
            NavigationVoiceCueStage.Early,
            cue(
                distanceMeters =
                    500.0,

                verbosity =
                    VoiceGuidanceVerbosity.Detailed,
            )
                ?.key
                ?.stage,
        )

        assertEquals(
            NavigationVoiceCueStage.Prepare,
            cue(
                distanceMeters =
                    180.0,

                verbosity =
                    VoiceGuidanceVerbosity.Detailed,
            )
                ?.key
                ?.stage,
        )

        assertEquals(
            NavigationVoiceCueStage.Now,
            cue(
                distanceMeters =
                    35.0,

                verbosity =
                    VoiceGuidanceVerbosity.Detailed,
            )
                ?.key
                ?.stage,
        )
    }

    @Test
    fun cueKeyIsStableForDeduplication() {
        val snapshot =
            snapshot(
                distanceMeters =
                    120.0
            )

        val voice =
            voice(
                verbosity =
                    VoiceGuidanceVerbosity.Standard
            )

        val first =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot,

                    voice =
                        voice,
                )

        val second =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot,

                    voice =
                        voice,
                )

        assertEquals(
            first?.key,
            second?.key,
        )
    }

    @Test
    fun cueCarriesOnlyVoiceOutputConfiguration() {
        val cue =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                20.0
                        ),

                    voice =
                        VoicePreferences(
                            enabled =
                                true,

                            languageTag =
                                "de-DE",

                            voiceId =
                                "local.voice",

                            speechRate =
                                1.15,

                            verbosity =
                                VoiceGuidanceVerbosity.Detailed,
                        ),
                )

        assertEquals(
            "In 120 Metern rechts abbiegen",
            cue?.text,
        )

        assertEquals(
            "de-DE",
            cue?.languageTag,
        )

        assertEquals(
            "local.voice",
            cue?.voiceId,
        )

        assertEquals(
            1.15,
            cue
                ?.speechRate
                ?: error(
                    "Expected voice cue."
                ),
            0.0,
        )
    }

    @Test
    fun experiencePackVerbosityFeedsCuePolicyWithoutMutatingSnapshot() {
        val snapshot =
            snapshot(
                distanceMeters =
                    180.0
            )

        val personality =
            NavigationPersonalityPreferences(
                selectedPackId =
                    ExperiencePackCatalog
                        .ZEN_PACK_ID,

                selectionSource =
                    ExperiencePackSelectionSource.Explicit,
            )

        val baseVoice =
            voice(
                verbosity =
                    VoiceGuidanceVerbosity.Detailed
            )

        val effectiveVoice =
            ExperiencePackRuntimeResolver
                .resolveVoicePreferences(
                    base =
                        baseVoice,

                    personality =
                        personality,

                    weekKey =
                        "2026-W37",
                )

        assertEquals(
            VoiceGuidanceVerbosity.Minimal,
            effectiveVoice.verbosity,
        )

        assertNull(
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot,

                    voice =
                        effectiveVoice,
                )
        )

        assertEquals(
            true,
            snapshot.presentationBoundaryIntact,
        )

        assertEquals(
            false,
            snapshot.rerouteRequested,
        )

        assertEquals(
            false,
            snapshot.routeRecomputed,
        )

        assertEquals(
            false,
            snapshot.routingEngineInvoked,
        )

        assertEquals(
            false,
            snapshot.candidateSelectionInvoked,
        )

        assertEquals(
            false,
            snapshot.costEngineInvoked,
        )

        assertEquals(
            false,
            snapshot.productionRouteMutationAllowed,
        )
    }

    @Test
    fun blankInstructionFailsClosed() {
        assertNull(
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                20.0,

                            instruction =
                                "   ",
                        ),

                    voice =
                        voice(),
                )
        )
    }

    private fun cue(
        distanceMeters:
            Double,

        verbosity:
            VoiceGuidanceVerbosity,
    ): NavigationVoiceCue? =
        NavigationVoicePresentation
            .cue(
                snapshot =
                    snapshot(
                        distanceMeters =
                            distanceMeters
                    ),

                voice =
                    voice(
                        verbosity =
                            verbosity
                    ),
            )

    private fun voice(
        enabled:
            Boolean =
            true,

        verbosity:
            VoiceGuidanceVerbosity =
            VoiceGuidanceVerbosity.Standard,
    ): VoicePreferences =
        VoicePreferences(
            enabled =
                enabled,

            verbosity =
                verbosity,
        )

    private fun snapshot(
        state:
            NavigationSessionState =
            NavigationSessionState.Navigating,

        arrived:
            Boolean =
            false,

        distanceMeters:
            Double,

        instruction:
            String =
            "In 120 Metern rechts abbiegen",

        maneuverType:
            ManeuverType =
            ManeuverType.TurnRight,
    ): NavigationUiSnapshot =
        NavigationUiSnapshot(
            sessionId =
                "session-voice",

            state =
                state,

            routeId =
                "route-voice",

            routeDistanceM =
                1000.0,

            routeDurationS =
                120.0,

            geometry =
                listOf(
                    RoutePoint(
                        latitude =
                            52.0,

                        longitude =
                            13.0,
                    ),

                    RoutePoint(
                        latitude =
                            52.01,

                        longitude =
                            13.01,
                    ),
                ),

            progressFraction =
                0.5,

            remainingDistanceM =
                500.0,

            remainingDurationS =
                60.0,

            currentManeuver =
                NavigationManeuver(
                    type =
                        maneuverType,

                    instruction =
                        instruction,

                    distanceM =
                        distanceMeters,

                    durationS =
                        10.0,

                    beginShapeIndex =
                        0,

                    endShapeIndex =
                        1,
                ),

            nextManeuver =
                null,

            distanceToCurrentManeuverEndM =
                distanceMeters,

            arrived =
                arrived,
        )

    @Test
    fun criticalRepeatUsesDistinctVoiceStageOnlyWhenEnabled() {
        val repeat =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                15.0,

                            maneuverType =
                                ManeuverType.Exit,
                        ),

                    voice =
                        voice(),

                    navigationPreferences =
                        NavigationPreferences(
                            repeatCriticalInstructions =
                                true
                        ),
                )

        val disabled =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                15.0,

                            maneuverType =
                                ManeuverType.Exit,
                        ),

                    voice =
                        voice(),

                    navigationPreferences =
                        NavigationPreferences(
                            repeatCriticalInstructions =
                                false
                        ),
                )

        val ordinary =
            NavigationVoicePresentation
                .cue(
                    snapshot =
                        snapshot(
                            distanceMeters =
                                15.0,

                            maneuverType =
                                ManeuverType.TurnRight,
                        ),

                    voice =
                        voice(),

                    navigationPreferences =
                        NavigationPreferences(
                            repeatCriticalInstructions =
                                true
                        ),
                )

        assertEquals(
            NavigationVoiceCueStage.CriticalRepeat,
            repeat?.key?.stage,
        )

        assertEquals(
            NavigationVoiceCueStage.Now,
            disabled?.key?.stage,
        )

        assertEquals(
            NavigationVoiceCueStage.Now,
            ordinary?.key?.stage,
        )
    }
}
