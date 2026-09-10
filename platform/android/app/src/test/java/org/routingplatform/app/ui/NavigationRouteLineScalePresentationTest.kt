package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteLineScalePresentationTest {

    @Test
    fun defaultScalePreservesExistingWidths() {
        assertEquals(
            6.0f,
            NavigationRouteLineScalePresentation
                .previewWidth(1.0),
            0.0001f,
        )

        assertEquals(
            7.0f,
            NavigationRouteLineScalePresentation
                .activeWidth(1.0),
            0.0001f,
        )
    }

    @Test
    fun supportedBoundsScaleOnlyStrokeWidth() {
        assertEquals(
            4.5f,
            NavigationRouteLineScalePresentation
                .previewWidth(0.75),
            0.0001f,
        )

        assertEquals(
            14.0f,
            NavigationRouteLineScalePresentation
                .activeWidth(2.0),
            0.0001f,
        )
    }

    @Test
    fun percentAndPresetConversionAreStable() {
        assertEquals(
            125,
            NavigationRouteLineScalePresentation
                .percent(1.25),
        )

        assertEquals(
            1.75,
            NavigationRouteLineScalePresentation
                .scaleForPercent(175),
            0.0000001,
        )
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun outOfRangeScaleIsRejected() {
        NavigationRouteLineScalePresentation
            .previewWidth(2.01)
    }

    @Test(
        expected =
            IllegalArgumentException::class
    )
    fun unsupportedPresetIsRejected() {
        NavigationRouteLineScalePresentation
            .scaleForPercent(110)
    }
}