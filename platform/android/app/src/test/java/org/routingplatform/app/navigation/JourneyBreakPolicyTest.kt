package org.routingplatform.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JourneyBreakPolicyTest {
    private fun stop(
        id: String = "wc-1",
        via: Long? = 1500,
        base: Long? = 1000,
        wc: JourneyBreakPolicy.WcEvidence = JourneyBreakPolicy.WcEvidence.VERIFIED,
        access: Boolean = true,
        sameDirection: Boolean = true,
        beforeJam: Boolean = true,
    ) = JourneyBreakPolicy.Stop(
        id, "Test WC", wc, access, sameDirection, base, via, beforeJam
    )

    private fun context(
        seconds: Long = 5401,
        remaining: Long = 60001,
        traffic: Boolean = false,
        urgent: Boolean = false,
        sinceOffer: Long? = null,
    ) = JourneyBreakPolicy.Context(seconds, remaining, sinceOffer, traffic, urgent)

    @Test fun thresholdsAreStrict() {
        assertEquals(JourneyBreakPolicy.DecisionReason.NOT_DUE,
            JourneyBreakPolicy.evaluate(context(seconds = 5400), listOf(stop())).reason)
        assertEquals(JourneyBreakPolicy.DecisionReason.NOT_DUE,
            JourneyBreakPolicy.evaluate(context(remaining = 60000), listOf(stop())).reason)
        assertNotNull(JourneyBreakPolicy.evaluate(context(), listOf(stop())).recommendation)
    }

    @Test fun detourIsSecondsPreciseAndFullRouteBased() {
        assertNotNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(via = 1600))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(via = 1601))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(via = 900))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(via = null))).recommendation)
    }

    @Test fun congestionMayPreemptDriveTimeButMustBeBeforeJam() {
        val c = context(seconds = 100, traffic = true)
        assertEquals(JourneyBreakPolicy.Trigger.CONGESTION,
            JourneyBreakPolicy.evaluate(c, listOf(stop())).recommendation?.trigger)
        assertNull(JourneyBreakPolicy.evaluate(c, listOf(stop(beforeJam = false))).recommendation)
    }

    @Test fun urgentUserRequestBypassesThresholdsAndCooldown() {
        val c = context(seconds = 10, remaining = 2000, urgent = true, sinceOffer = 5)
        assertEquals(JourneyBreakPolicy.Trigger.URGENT_USER_REQUEST,
            JourneyBreakPolicy.evaluate(c, listOf(stop())).recommendation?.trigger)
        assertNull(JourneyBreakPolicy.evaluate(c, listOf(stop(wc = JourneyBreakPolicy.WcEvidence.UNKNOWN))).recommendation)
    }

    @Test fun accessAndEvidenceAreMandatory() {
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(access = false))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(sameDirection = false))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(wc = JourneyBreakPolicy.WcEvidence.UNKNOWN))).recommendation)
        assertNull(JourneyBreakPolicy.evaluate(context(), listOf(stop(wc = JourneyBreakPolicy.WcEvidence.ABSENT))).recommendation)
    }

    @Test fun cooldownAndInvalidInputsFailClosed() {
        assertEquals(JourneyBreakPolicy.DecisionReason.COOLDOWN,
            JourneyBreakPolicy.evaluate(context(sinceOffer = 100), listOf(stop())).reason)
        assertEquals(JourneyBreakPolicy.DecisionReason.INVALID_CONTEXT,
            JourneyBreakPolicy.evaluate(context(seconds = -1), listOf(stop())).reason)
    }

    @Test fun offersAtMostThreeDeterministically() {
        val options = (1..7).map { stop(id = "wc-$it", via = 1000L + it * 10L) }
        assertEquals(listOf("wc-1", "wc-2", "wc-3"),
            JourneyBreakPolicy.evaluate(context(), options).recommendation?.stops?.map { it.id })
    }
}
