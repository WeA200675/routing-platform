package org.routingplatform.app.navigation

import java.time.Instant
import java.util.Locale

/** Legal admission gate for speed-enforcement location alerts. */
enum class EnforcementAlertLegality {
    Permitted,
    Prohibited,
    Unverified,
}

data class EnforcementAlertCountryRule(
    val countryIso2: String,
    val legality: EnforcementAlertLegality,
    val sourceUrl: String,
    val reviewedAt: Instant,
    val expiresAt: Instant,
) {
    init {
        require(countryIso2.matches(Regex("[A-Z]{2}")))
        require(sourceUrl.startsWith("https://"))
        require(expiresAt.isAfter(reviewedAt))
    }
}

/**
 * Camera alert rules are fail-closed. Missing, stale, or ambiguous country
 * data never enables an alert. An alert may only be admitted by an explicit,
 * current, reviewed country rule.
 */
object SpeedEnforcementAlertPolicy {
    fun mayPresentAlert(
        countryIso2: String?,
        userEnabled: Boolean,
        rule: EnforcementAlertCountryRule?,
        now: Instant = Instant.now(),
    ): Boolean {
        if (!userEnabled) return false
        val country = countryIso2
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.matches(Regex("[A-Z]{2}")) }
            ?: return false
        rule ?: return false
        if (rule.countryIso2 != country) return false
        if (rule.legality != EnforcementAlertLegality.Permitted) return false
        if (now.isBefore(rule.reviewedAt) || !now.isBefore(rule.expiresAt)) return false
        return true
    }
}
