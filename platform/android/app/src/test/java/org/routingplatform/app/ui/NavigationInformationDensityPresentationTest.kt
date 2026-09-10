package org.routingplatform.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.routingplatform.app.profile.InformationDensityPreference

class NavigationInformationDensityPresentationTest {

    @Test
    fun minimalKeepsOnlyPrimaryDistanceFact() {
        val presentation =
            NavigationInformationDensityPresentation
                .create(
                    InformationDensityPreference.Minimal
                )

        assertFalse(presentation.showRemainingDuration)
        assertFalse(presentation.showProgress)
        assertFalse(presentation.showManeuverDistance)
    }

    @Test
    fun standardPreservesPreG614Metrics() {
        val presentation =
            NavigationInformationDensityPresentation
                .create(
                    InformationDensityPreference.Standard
                )

        assertTrue(presentation.showRemainingDuration)
        assertTrue(presentation.showProgress)
        assertFalse(presentation.showManeuverDistance)
    }

    @Test
    fun detailedAddsManeuverDistanceWithoutRemovingBaselineFacts() {
        val presentation =
            NavigationInformationDensityPresentation
                .create(
                    InformationDensityPreference.Detailed
                )

        assertTrue(presentation.showRemainingDuration)
        assertTrue(presentation.showProgress)
        assertTrue(presentation.showManeuverDistance)
    }

    @Test
    fun labelsAreStableGermanPresentationText() {
        assertEquals(
            "Minimal",
            NavigationInformationDensityPresentation
                .label(InformationDensityPreference.Minimal),
        )
        assertEquals(
            "Standard",
            NavigationInformationDensityPresentation
                .label(InformationDensityPreference.Standard),
        )
        assertEquals(
            "Detailliert",
            NavigationInformationDensityPresentation
                .label(InformationDensityPreference.Detailed),
        )
    }
}