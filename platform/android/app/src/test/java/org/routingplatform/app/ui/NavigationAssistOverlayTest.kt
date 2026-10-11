package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.routingplatform.app.navigation.NavigationRawGnssQuality

class NavigationAssistOverlayTest {
    @Test
    fun weakGnssQualityShowsActionablePhonePlacementAdvice() {
        assertEquals(
            "GNSS-Empfang: schwach",
            gnssReceptionLabel(NavigationRawGnssQuality.Weak),
        )
        assertEquals(
            "Handy höher und näher an die Frontscheibe setzen; Halterung testweise abnehmen.",
            gnssReceptionAdvice(NavigationRawGnssQuality.Weak),
        )
    }

    @Test
    fun strongGnssQualityDoesNotShowUnneededAdvice() {
        assertEquals(
            "GNSS-Empfang: gut",
            gnssReceptionLabel(NavigationRawGnssQuality.Strong),
        )
        assertNull(
            gnssReceptionAdvice(NavigationRawGnssQuality.Strong),
        )
    }

    @Test
    fun missingGnssMeasurementsAreNotReportedAsGood() {
        assertEquals(
            "GNSS-Qualität nicht verfügbar",
            gnssReceptionLabel(null),
        )
        assertEquals(
            "Ortungsfreigabe und freie Sicht zum Himmel prüfen.",
            gnssReceptionAdvice(null),
        )
    }
}
