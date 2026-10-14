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
    fun contradictorySideSpecificUTurnInstructionIsCorrected() {
        val maneuver = maneuver(
            type = ManeuverType.UTurn,
            engineType = 13,
            instruction = "Rechts wenden.",
        )

        assertEquals("Links wenden. Bitte Beschilderung beachten.", maneuver.guidanceInstruction)
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
