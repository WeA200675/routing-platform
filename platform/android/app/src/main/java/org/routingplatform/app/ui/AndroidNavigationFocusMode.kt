package org.routingplatform.app.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

internal data class NavigationFocusModeRuntime(
    val presentation:
        NavigationFocusPresentation,

    val notifyUserActivity:
        () -> Unit,

    val setInteractionActive:
        (Boolean) -> Unit,
)

/*
 * Android/Compose timing adapter for the pure G6.8 presentation policy.
 *
 * SystemClock.elapsedRealtime is used so wall-clock changes cannot
 * accidentally collapse or reveal controls.
 */
@Composable
internal fun rememberNavigationFocusMode(
    navigationActive:
        Boolean,
): NavigationFocusModeRuntime {
    val navigationStartedElapsedRealtimeMs =
        remember(
            navigationActive
        ) {
            if (navigationActive) {
                SystemClock
                    .elapsedRealtime()
            } else {
                0L
            }
        }

    var lastUserActivityElapsedRealtimeMs by
        remember(
            navigationActive
        ) {
            mutableStateOf<Long?>(
                null
            )
        }

    var interactionActive by
        remember(
            navigationActive
        ) {
            mutableStateOf(
                false
            )
        }

    var nowElapsedRealtimeMs by
        remember(
            navigationActive
        ) {
            mutableLongStateOf(
                SystemClock
                    .elapsedRealtime()
            )
        }

    val collapseDeadlineElapsedRealtimeMs =
        if (navigationActive) {
            NavigationFocusModePolicy
                .collapseDeadlineElapsedRealtimeMs(
                    navigationStartedElapsedRealtimeMs =
                        navigationStartedElapsedRealtimeMs,

                    lastUserActivityElapsedRealtimeMs =
                        lastUserActivityElapsedRealtimeMs,
                )
        } else {
            null
        }

    LaunchedEffect(
        navigationActive,
        interactionActive,
        collapseDeadlineElapsedRealtimeMs,
    ) {
        nowElapsedRealtimeMs =
            SystemClock
                .elapsedRealtime()

        val deadline =
            collapseDeadlineElapsedRealtimeMs
                ?: return@LaunchedEffect

        if (interactionActive) {
            return@LaunchedEffect
        }

        val remainingMs =
            deadline -
                nowElapsedRealtimeMs

        if (remainingMs > 0L) {
            delay(
                remainingMs
            )
        }

        nowElapsedRealtimeMs =
            SystemClock
                .elapsedRealtime()
    }

    val presentation =
        NavigationFocusModePolicy
            .presentation(
                navigationActive =
                    navigationActive,

                navigationStartedElapsedRealtimeMs =
                    navigationStartedElapsedRealtimeMs,

                lastUserActivityElapsedRealtimeMs =
                    lastUserActivityElapsedRealtimeMs,

                interactionActive =
                    interactionActive,

                nowElapsedRealtimeMs =
                    nowElapsedRealtimeMs,
            )

    val notifyUserActivity:
        () -> Unit = {
        if (navigationActive) {
            val now =
                SystemClock
                    .elapsedRealtime()

            lastUserActivityElapsedRealtimeMs =
                now

            nowElapsedRealtimeMs =
                now
        }
    }

    val setInteractionActive:
        (Boolean) -> Unit = {
            active ->

        if (navigationActive) {
            if (
                interactionActive !=
                active
            ) {
                val now =
                    SystemClock
                        .elapsedRealtime()

                interactionActive =
                    active

                /*
                 * Both gesture start and gesture end count as activity.
                 * Ending an interaction therefore starts a fresh reveal
                 * grace period instead of hiding controls immediately.
                 */
                lastUserActivityElapsedRealtimeMs =
                    now

                nowElapsedRealtimeMs =
                    now
            }
        } else if (
            interactionActive
        ) {
            interactionActive =
                false
        }
    }

    return NavigationFocusModeRuntime(
        presentation =
            presentation,

        notifyUserActivity =
            notifyUserActivity,

        setInteractionActive =
            setInteractionActive,
    )
}