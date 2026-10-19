package org.routingplatform.app.navigation

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedEnforcementAlertPolicyTest {
    private val now = Instant.parse("2026-10-14T08:00:00Z")

    private fun rule(
        country: String = "XX",
        legality: EnforcementAlertLegality = EnforcementAlertLegality.Permitted,
        expiresAt: Instant = now.plusSeconds(3600),
    ) = EnforcementAlertCountryRule(
        countryIso2 = country,
        legality = legality,
        sourceUrl = "https://example.invalid/legal-source",
        reviewedAt = now.minusSeconds(60),
        expiresAt = expiresAt,
    )

    @Test
    fun onlyExplicitCurrentPermittedCountryRuleAdmitsAlert() {
        assertTrue(SpeedEnforcementAlertPolicy.mayPresentAlert("xx", true, rule(), now))
        assertFalse(SpeedEnforcementAlertPolicy.mayPresentAlert("DE", true, rule(), now))
        assertFalse(SpeedEnforcementAlertPolicy.mayPresentAlert("XX", true, null, now))
        assertFalse(SpeedEnforcementAlertPolicy.mayPresentAlert("XX", false, rule(), now))
    }

    @Test
    fun prohibitedUnverifiedAndExpiredRulesFailClosed() {
        assertFalse(
            SpeedEnforcementAlertPolicy.mayPresentAlert(
                "XX", true, rule(legality = EnforcementAlertLegality.Prohibited), now,
            ),
        )
        assertFalse(
            SpeedEnforcementAlertPolicy.mayPresentAlert(
                "XX", true, rule(legality = EnforcementAlertLegality.Unverified), now,
            ),
        )
        assertFalse(
            SpeedEnforcementAlertPolicy.mayPresentAlert(
                "XX", true, rule(expiresAt = now), now,
            ),
        )
        assertFalse(SpeedEnforcementAlertPolicy.mayPresentAlert(null, true, rule(), now))
    }
}
