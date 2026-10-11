package org.routingplatform.app.ui

internal const val NAVIGATION_FOCUS_INITIAL_EXPANDED_MS =
    30_000L

internal const val NAVIGATION_FOCUS_REVEAL_AFTER_ACTIVITY_MS =
    8_000L

/*
 * Presentation-only output for G6.8.
 *
 * This state controls visibility of navigation chrome only.
 * It carries no routing, progress, positioning, rerouting or safety
 * authority and cannot mutate navigation truth.
 */
internal data class NavigationFocusPresentation(
    val navigationActive: Boolean,
    val controlsVisible: Boolean,
    val interactionActive: Boolean,
) {
    val focused: Boolean
        get() =
            navigationActive &&
                !controlsVisible

    val showTopChrome: Boolean
        get() =
            !focused

    val showSecondaryControls: Boolean
        get() =
            !focused
}

internal object NavigationFocusModePolicy {
    fun collapseDeadlineElapsedRealtimeMs(
        navigationStartedElapsedRealtimeMs:
            Long,

        lastUserActivityElapsedRealtimeMs:
            Long?,
    ): Long {
        require(
            navigationStartedElapsedRealtimeMs >=
                0L
        )

        require(
            lastUserActivityElapsedRealtimeMs ==
                null ||
                lastUserActivityElapsedRealtimeMs >=
                0L
        )

        val initialDeadline =
            safeAdd(
                navigationStartedElapsedRealtimeMs,
                NAVIGATION_FOCUS_INITIAL_EXPANDED_MS,
            )

        val activityDeadline =
            lastUserActivityElapsedRealtimeMs
                ?.let {
                    safeAdd(
                        it,
                        NAVIGATION_FOCUS_REVEAL_AFTER_ACTIVITY_MS,
                    )
                }
                ?: Long.MIN_VALUE

        return maxOf(
            initialDeadline,
            activityDeadline,
        )
    }

    fun presentation(
        navigationActive:
            Boolean,

        navigationStartedElapsedRealtimeMs:
            Long,

        lastUserActivityElapsedRealtimeMs:
            Long?,

        interactionActive:
            Boolean,

        nowElapsedRealtimeMs:
            Long,
    ): NavigationFocusPresentation {
        require(
            nowElapsedRealtimeMs >=
                0L
        )

        if (!navigationActive) {
            return NavigationFocusPresentation(
                navigationActive =
                    false,
                controlsVisible =
                    true,
                interactionActive =
                    false,
            )
        }

        val deadline =
            collapseDeadlineElapsedRealtimeMs(
                navigationStartedElapsedRealtimeMs =
                    navigationStartedElapsedRealtimeMs,
                lastUserActivityElapsedRealtimeMs =
                    lastUserActivityElapsedRealtimeMs,
            )

        return NavigationFocusPresentation(
            navigationActive =
                true,
            controlsVisible =
                interactionActive ||
                    nowElapsedRealtimeMs <
                    deadline,
            interactionActive =
                interactionActive,
        )
    }

    private fun safeAdd(
        base:
            Long,

        duration:
            Long,
    ): Long {
        require(
            base >=
                0L
        )

        require(
            duration >=
                0L
        )

        return if (
            base >
            Long.MAX_VALUE -
                duration
        ) {
            Long.MAX_VALUE
        } else {
            base +
                duration
        }
    }
}