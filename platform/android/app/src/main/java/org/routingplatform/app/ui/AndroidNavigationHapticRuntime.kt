package org.routingplatform.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.NavigationHapticIntensity
import org.routingplatform.app.profile.NavigationPreferences

internal data class NavigationHapticRuntimePresentation(
    val available:
        Boolean,

    val preview:
        (NavigationHapticIntensity) ->
        Boolean,
)

internal class AndroidNavigationHapticRuntime(
    context:
        Context,
) :
    AutoCloseable {

    private val sink =
        AndroidNavigationHapticSink(
            context.applicationContext
        )

    private val playback =
        NavigationHapticPlaybackRuntime(
            sink
        )

    val available:
        Boolean
        get() =
            sink.available

    fun present(
        snapshot:
            NavigationUiSnapshot,

        preferences:
            NavigationPreferences,
    ) {
        playback.present(
            snapshot =
                snapshot,

            preferences =
                preferences,
        )
    }

    fun preview(
        intensity:
            NavigationHapticIntensity,
    ): Boolean =
        sink.preview(
            intensity
        )

    override fun close() {
        playback.close()
    }
}

@Composable
internal fun rememberNavigationHapticRuntime(
    snapshot:
        NavigationUiSnapshot,

    navigationPreferences:
        NavigationPreferences,
): NavigationHapticRuntimePresentation {
    val context =
        LocalContext
            .current
            .applicationContext

    val runtime =
        remember(
            context
        ) {
            AndroidNavigationHapticRuntime(
                context
            )
        }

    LaunchedEffect(
        runtime,
        snapshot,
        navigationPreferences,
    ) {
        runtime.present(
            snapshot =
                snapshot,

            preferences =
                navigationPreferences,
        )
    }

    DisposableEffect(
        runtime
    ) {
        onDispose {
            runtime.close()
        }
    }

    return NavigationHapticRuntimePresentation(
        available =
            runtime.available,

        preview = {
                intensity ->

            runtime.preview(
                intensity
            )
        },
    )
}

private class AndroidNavigationHapticSink(
    context:
        Context,
) :
    NavigationHapticSink {

    private val vibrator:
        Vibrator? =
        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S
        ) {
            context
                .getSystemService(
                    VibratorManager::class.java
                )
                ?.defaultVibrator
        } else {
            @Suppress(
                "DEPRECATION"
            )
            (
                context.getSystemService(
                    Context.VIBRATOR_SERVICE
                ) as?
                    Vibrator
            )
        }

    val available:
        Boolean
        get() =
            runCatching {
                vibrator
                    ?.hasVibrator() ==
                    true
            }.getOrDefault(
                false
            )

    override fun submit(
        cue:
            NavigationHapticCue,
    ): NavigationHapticSubmission =
        if (
            perform(
                signal =
                    cue.signal,

                intensity =
                    cue.intensity,
            )
        ) {
            NavigationHapticSubmission
                .Performed
        } else {
            NavigationHapticSubmission
                .Rejected
        }

    fun preview(
        intensity:
            NavigationHapticIntensity,
    ): Boolean =
        perform(
            signal =
                NavigationHapticSignal
                    .Now,

            intensity =
                intensity,
        )

    private fun perform(
        signal:
            NavigationHapticSignal,

        intensity:
            NavigationHapticIntensity,
    ): Boolean {
        val engine =
            vibrator
                ?: return false

        if (
            !available
        ) {
            return false
        }

        val amplitudeControl =
            runCatching {
                engine
                    .hasAmplitudeControl()
            }.getOrDefault(
                false
            )

        val plan =
            NavigationHapticEffectPlanner
                .create(
                    signal =
                        signal,

                    intensity =
                        intensity,

                    amplitudeControl =
                        amplitudeControl,
                )

        val effect =
            VibrationEffect
                .createWaveform(
                    plan.timings,
                    plan.amplitudes,
                    -1,
                )

        return runCatching {
            engine.vibrate(
                effect
            )

            true
        }.getOrDefault(
            false
        )
    }

    override fun cancel() {
        runCatching {
            vibrator
                ?.cancel()
        }
    }

    override fun close() {
        cancel()
    }
}