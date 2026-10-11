package org.routingplatform.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationControlSideUiTestTagsTest {

    @Test
    fun navigationControlSideTagsAreStableUniqueAndNamespaced() {
        assertEquals(
            "rp.navigation.primary_control_move_side",
            NavigationUiTestTags.PrimaryControlMoveSide,
        )

        assertEquals(
            "rp.navigation.primary_control_side.Left",
            NavigationUiTestTags
                .primaryControlSide("Left"),
        )

        assertEquals(
            "rp.navigation.primary_control_side.Right",
            NavigationUiTestTags
                .primaryControlSide("Right"),
        )

        val tags =
            listOf(
                NavigationUiTestTags.PrimaryAction,
                NavigationUiTestTags.PrimaryControlMoveSide,
                NavigationUiTestTags.primaryControlSide("Left"),
                NavigationUiTestTags.primaryControlSide("Right"),
            )

        assertEquals(
            tags.size,
            tags.toSet().size,
        )

        assertTrue(
            tags.all {
                it.startsWith("rp.navigation.")
            }
        )
    }
}