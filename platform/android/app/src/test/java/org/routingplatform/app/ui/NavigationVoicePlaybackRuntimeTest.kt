package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.routingplatform.app.navigation.ManeuverType
import org.routingplatform.app.navigation.NavigationManeuver
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.profile.VoiceGuidanceVerbosity
import org.routingplatform.app.profile.VoicePreferences

class NavigationVoicePlaybackRuntimeTest {
    @Test
    fun duplicateCueIsSubmittedOnce() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val snapshot = snapshot(distanceMeters = 35.0)

        runtime.present(snapshot, voice())
        runtime.present(snapshot, voice())

        assertEquals(1, speaker.submitted.size)
    }

    @Test
    fun stageProgressionSubmitsEachStageOnce() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val detailed =
            voice(
                verbosity = VoiceGuidanceVerbosity.Detailed
            )

        runtime.present(
            snapshot(distanceMeters = 500.0),
            detailed,
        )
        runtime.present(
            snapshot(distanceMeters = 180.0),
            detailed,
        )
        runtime.present(
            snapshot(distanceMeters = 35.0),
            detailed,
        )

        assertEquals(
            listOf(
                NavigationVoiceCueStage.Early,
                NavigationVoiceCueStage.Prepare,
                NavigationVoiceCueStage.Now,
            ),
            speaker.submitted.map {
                it.key.stage
            },
        )
    }

    @Test
    fun distanceJitterDoesNotReplayDeliveredStage() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val detailed =
            voice(
                verbosity = VoiceGuidanceVerbosity.Detailed
            )

        runtime.present(
            snapshot(distanceMeters = 500.0),
            detailed,
        )
        runtime.present(
            snapshot(distanceMeters = 180.0),
            detailed,
        )
        runtime.present(
            snapshot(distanceMeters = 300.0),
            detailed,
        )

        assertEquals(2, speaker.submitted.size)
    }

    @Test
    fun missingShapeIndicesStillDeduplicateSameManeuver() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)

        runtime.present(
            snapshot(
                distanceMeters = 35.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        runtime.present(
            snapshot(
                distanceMeters = 20.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        assertEquals(
            1,
            speaker.submitted.size,
        )
    }

    @Test
    fun missingShapeIndicesIgnoreOrdinaryDistanceJitter() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)

        runtime.present(
            snapshot(
                distanceMeters = 180.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        runtime.present(
            snapshot(
                distanceMeters = 220.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        runtime.present(
            snapshot(
                distanceMeters = 180.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        assertEquals(
            1,
            speaker.submitted.size,
        )
    }

    @Test
    fun identicalFallbackManeuverAfterDistanceResetCanSpeakAgain() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)

        runtime.present(
            snapshot(
                distanceMeters = 35.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        /*
         * Same instruction/type, but the maneuver distance jumps forward by
         * more than the playback-only fallback reset threshold. This models a
         * distinct subsequent maneuver when shape indices are unavailable.
         */
        runtime.present(
            snapshot(
                distanceMeters = 200.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        runtime.present(
            snapshot(
                distanceMeters = 35.0,
                withShapeIndices = false,
            ),
            voice(),
        )

        assertEquals(
            2,
            speaker.submitted.size,
        )

        assertEquals(
            listOf(
                NavigationVoiceCueStage.Now,
                NavigationVoiceCueStage.Now,
            ),
            speaker.submitted.map {
                it.key.stage
            },
        )
    }

    @Test
    fun newSessionResetsDeduplication() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)

        runtime.present(
            snapshot(
                sessionId = "session-a",
                distanceMeters = 35.0,
            ),
            voice(),
        )
        runtime.present(
            snapshot(
                sessionId = "session-b",
                distanceMeters = 35.0,
            ),
            voice(),
        )

        assertEquals(2, speaker.submitted.size)
        assertEquals(1, speaker.stopCount)
    }

    @Test
    fun inactiveStateStopsAndClearsPlaybackSession() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)

        runtime.present(
            snapshot(distanceMeters = 35.0),
            voice(),
        )
        runtime.present(
            snapshot(
                state = NavigationSessionState.Preview,
                distanceMeters = 35.0,
            ),
            voice(),
        )
        runtime.present(
            snapshot(distanceMeters = 35.0),
            voice(),
        )

        assertEquals(2, speaker.submitted.size)
        assertEquals(1, speaker.stopCount)
    }

    @Test
    fun rejectedSubmissionCanRetry() {
        val speaker =
            FakeSpeaker(
                submission = NavigationSpeechSubmission.Rejected
            )
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val snapshot = snapshot(distanceMeters = 35.0)

        runtime.present(snapshot, voice())
        runtime.present(snapshot, voice())

        assertEquals(2, speaker.submitted.size)
    }

    @Test
    fun disabledVoiceStopsActivePlayback() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val snapshot = snapshot(distanceMeters = 35.0)

        runtime.present(snapshot, voice())
        runtime.present(
            snapshot,
            voice(enabled = false),
        )

        assertEquals(1, speaker.stopCount)
    }

    @Test
    fun playbackDoesNotMutateNavigationSafetyBoundary() {
        val speaker = FakeSpeaker()
        val runtime = NavigationVoicePlaybackRuntime(speaker)
        val snapshot = snapshot(distanceMeters = 35.0)

        runtime.present(snapshot, voice())

        assertTrue(snapshot.presentationBoundaryIntact)
        assertEquals(false, snapshot.rerouteRequested)
        assertEquals(false, snapshot.routeRecomputed)
        assertEquals(false, snapshot.routingEngineInvoked)
        assertEquals(false, snapshot.candidateSelectionInvoked)
        assertEquals(false, snapshot.costEngineInvoked)
        assertEquals(false, snapshot.productionRouteMutationAllowed)
    }

    private class FakeSpeaker(
        var submission: NavigationSpeechSubmission =
            NavigationSpeechSubmission.Spoken,
    ) : NavigationVoiceSpeaker {
        val submitted = mutableListOf<NavigationVoiceCue>()
        var stopCount = 0

        override fun submit(
            cue: NavigationVoiceCue,
        ): NavigationSpeechSubmission {
            submitted.add(cue)
            return submission
        }

        override fun clearPending() = Unit

        override fun stop() {
            stopCount += 1
        }

        override fun close() = Unit
    }

    private fun voice(
        enabled: Boolean = true,
        verbosity: VoiceGuidanceVerbosity =
            VoiceGuidanceVerbosity.Standard,
    ): VoicePreferences =
        VoicePreferences(
            enabled = enabled,
            languageTag = "de-DE",
            voiceId = "device.voice",
            speechRate = 0.95,
            verbosity = verbosity,
        )

    private fun snapshot(
        sessionId: String = "session-voice-runtime",
        state: NavigationSessionState =
            NavigationSessionState.Navigating,
        distanceMeters: Double,
        withShapeIndices: Boolean = true,
    ): NavigationUiSnapshot =
        NavigationUiSnapshot(
            sessionId = sessionId,
            state = state,
            routeId = "route-voice-runtime",
            routeDistanceM = 1000.0,
            routeDurationS = 120.0,
            geometry =
                listOf(
                    RoutePoint(
                        latitude = 52.0,
                        longitude = 13.0,
                    ),
                    RoutePoint(
                        latitude = 52.01,
                        longitude = 13.01,
                    ),
                ),
            progressFraction = 0.5,
            remainingDistanceM = 500.0,
            remainingDurationS = 60.0,
            currentManeuver =
                NavigationManeuver(
                    type = ManeuverType.TurnRight,
                    instruction =
                        "In 120 Metern rechts abbiegen",
                    distanceM = distanceMeters,
                    durationS = 10.0,
                    beginShapeIndex =
                        if (withShapeIndices) {
                            2
                        } else {
                            null
                        },
                    endShapeIndex =
                        if (withShapeIndices) {
                            3
                        } else {
                            null
                        },
                ),
            nextManeuver = null,
            distanceToCurrentManeuverEndM =
                distanceMeters,
            arrived =
                state == NavigationSessionState.Arrived,
        )
}