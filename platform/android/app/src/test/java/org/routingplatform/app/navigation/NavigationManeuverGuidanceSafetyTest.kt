package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NavigationManeuverGuidanceSafetyTest {
    @Test
    fun screenshotLikeWrongLeftExitInstructionIsCorrectedFromValhallaExitRight() {
        val maneuver = maneuver(
            type = ManeuverType.Exit,
            engineType = 20,
            instruction = "Auf A 92/E 53 über die Ausfahrt LINKS halten.",
        )

        assertEquals(
            "Ausfahrt rechts nehmen. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    @Test
    fun ambiguousExitSideIsRemovedWhenRoutingEngineTypeIsMissing() {
        val maneuver = maneuver(
            type = ManeuverType.Exit,
            engineType = null,
            instruction = "Auf A 92/E 53 über die Ausfahrt LINKS halten.",
        )

        assertEquals(
            "Ausfahrt nehmen. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
        assertFalse(maneuver.guidanceInstruction.contains("links", ignoreCase = true))
    }

    @Test
    fun correctValhallaExitLeftInstructionIsPreserved() {
        val maneuver = maneuver(
            type = ManeuverType.Exit,
            engineType = 21,
            instruction = "Ausfahrt links nehmen.",
        )

        assertEquals("Ausfahrt links nehmen.", maneuver.guidanceInstruction)
    }

    @Test
    fun normalizedProviderExitSideCorrectsContradictoryText() {
        val maneuver = NavigationManeuver(
            type = ManeuverType.Exit,
            instruction = "Auf die Ausfahrt links halten.",
            distanceM = 120.0,
            durationS = 12.0,
            maneuverSide = NavigationManeuverSide.Right,
        )

        assertEquals(
            "Ausfahrt rechts nehmen. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    @Test
    fun contradictorySideSpecificUTurnInstructionIsCorrected() {
        val maneuver = maneuver(
            type = ManeuverType.UTurn,
            engineType = 13,
            instruction = "Rechts wenden.",
        )

        assertEquals("Links wenden. Bitte Beschilderung beachten.", maneuver.guidanceInstruction)
    }

    @Test
    fun contradictoryRouteBearingsSuppressDirectionAndRoadName() {
        val maneuver = NavigationManeuver(
            type = ManeuverType.TurnRight,
            instruction = "Rechts auf die Süddeutsche Straße abbiegen.",
            distanceM = 120.0,
            durationS = 12.0,
            streetNames = listOf("Süddeutsche Straße"),
            bearingBeforeDeg = 90,
            bearingAfterDeg = 0,
            engineType = 10,
        )

        assertEquals(
            "Manöverdaten widersprüchlich. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    @Test
    fun consistentBearingsStillCorrectContradictoryProviderText() {
        val maneuver = NavigationManeuver(
            type = ManeuverType.TurnLeft,
            instruction = "In die Straße rechts abbiegen.",
            distanceM = 120.0,
            durationS = 12.0,
            bearingBeforeDeg = 90,
            bearingAfterDeg = 0,
            engineType = 15,
        )

        assertEquals(
            "Links abbiegen. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    @Test
    fun doesNotSpeakStreetNameThatConflictsWithRouteStreetMetadata() {
        val maneuver = NavigationManeuver(
            type = ManeuverType.TurnRight,
            instruction = "Rechts auf die Süddeutsche Straße abbiegen.",
            distanceM = 120.0,
            durationS = 12.0,
            streetNames = listOf("Ingolstädter Straße"),
            bearingBeforeDeg = 90,
            bearingAfterDeg = 180,
            engineType = 10,
        )

        assertEquals(
            "Rechts abbiegen. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    @Test
    fun conflictingBearingWinsBeforeWrongStreetNameCanBeSanitized() {
        val maneuver = NavigationManeuver(
            type = ManeuverType.TurnRight,
            instruction = "Rechts auf die Süddeutsche Straße abbiegen.",
            distanceM = 120.0,
            durationS = 12.0,
            streetNames = listOf("Ingolstädter Straße"),
            bearingBeforeDeg = 90,
            bearingAfterDeg = 0,
            engineType = 10,
        )

        assertEquals(
            "Manöverdaten widersprüchlich. Bitte Beschilderung beachten.",
            maneuver.guidanceInstruction,
        )
    }

    private fun maneuver(
        type: ManeuverType,
        engineType: Int?,
        instruction: String,
    ) = NavigationManeuver(
        type = type,
        instruction = instruction,
        distanceM = 120.0,
        durationS = 12.0,
        engineType = engineType,
    )
}
