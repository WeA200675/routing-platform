package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTextScalePresentationTest {

    @Test
    fun profileScaleMultipliesExistingSystemFontScale() {
        assertEquals(
            1.5f,
            NavigationTextScalePresentation
                .resolve(
                    systemFontScale =
                        1.2f,

                    profileTextScale =
                        1.25,
                ),
            0.0001f,
        )
    }

    @Test
    fun validProfileBoundaryValuesAreAccepted() {
        assertTrue(
            NavigationTextScalePresentation
                .isSupportedProfileScale(
                    0.8
                )
        )

        assertTrue(
            NavigationTextScalePresentation
                .isSupportedProfileScale(
                    1.5
                )
        )

        assertEquals(
            80,
            NavigationTextScalePresentation
                .percent(
                    0.8
                )
        )

        assertEquals(
            150,
            NavigationTextScalePresentation
                .percent(
                    1.5
                )
        )
    }

    @Test
    fun invalidProfileScaleFailsClosedToSystemFontScale() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            0.79,
            1.51,
        ).forEach {
                invalid ->

            assertFalse(
                NavigationTextScalePresentation
                    .isSupportedProfileScale(
                        invalid
                    )
            )

            assertEquals(
                1.3f,
                NavigationTextScalePresentation
                    .resolve(
                        systemFontScale =
                            1.3f,

                        profileTextScale =
                            invalid,
                    ),
                0.0001f,
            )
        }
    }

    @Test
    fun invalidSystemFontScaleFailsClosedToOne() {
        assertEquals(
            1.0f,
            NavigationTextScalePresentation
                .resolve(
                    systemFontScale =
                        Float.NaN,

                    profileTextScale =
                        1.2,
                ),
            0.0001f,
        )

        assertEquals(
            1.0f,
            NavigationTextScalePresentation
                .resolve(
                    systemFontScale =
                        0.0f,

                    profileTextScale =
                        1.2,
                ),
            0.0001f,
        )
    }
}