package org.routingplatform.app.ui

import android.content.Context
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val RECENT_LUX_MAX_AGE_MS =
    5_000L

private data class NavigationLuxSample(
    val sequence: Long,
    val lux: Double,
    val elapsedRealtimeMs: Long,
)

@Composable
internal fun rememberNavigationNightAdaptation(
    activity: ComponentActivity,
    navigationActive: Boolean,
    brightnessCorrection: Int,
): NavigationNightPresentation {
    val lifecycleOwner =
        LocalLifecycleOwner.current

    val sensorManager =
        remember(activity) {
            activity
                .applicationContext
                .getSystemService(
                    Context.SENSOR_SERVICE
                ) as SensorManager
        }

    val lightSensor =
        remember(sensorManager) {
            sensorManager.getDefaultSensor(
                Sensor.TYPE_LIGHT
            )
        }

    val sensorAvailable =
        lightSensor !=
            null

    val originalWindowBrightness =
        remember(activity) {
            activity
                .window
                .attributes
                .screenBrightness
        }

    var sampleSequence by
        remember {
            mutableLongStateOf(
                0L
            )
        }

    var latestSample by
        remember {
            mutableStateOf<
                NavigationLuxSample?
            >(
                null
            )
        }

    var smoothedLux by
        remember {
            mutableStateOf<Double?>(
                null
            )
        }

    var previousNightMode by
        remember {
            mutableStateOf(
                currentSystemNightMode(
                    activity
                )
            )
        }

    var processedSampleSequence by
        remember {
            mutableLongStateOf(
                -1L
            )
        }

    var presentation by
        remember {
            mutableStateOf(
                NavigationNightPresentation
                    .inactive(
                        systemNightMode =
                            previousNightMode
                    )
            )
        }

    DisposableEffect(
        lifecycleOwner,
        sensorManager,
        lightSensor,
    ) {
        if (lightSensor == null) {
            onDispose {
                Unit
            }
        } else {
            var registered =
                false

            val listener =
                object :
                    SensorEventListener {
                    override fun onSensorChanged(
                        event:
                            SensorEvent,
                    ) {
                        val lux =
                            event
                                .values
                                .firstOrNull()
                                ?.toDouble()
                                ?: return

                        if (
                            !lux.isFinite() ||
                            lux <
                                0.0
                        ) {
                            return
                        }

                        sampleSequence +=
                            1L

                        latestSample =
                            NavigationLuxSample(
                                sequence =
                                    sampleSequence,
                                lux =
                                    lux,
                                elapsedRealtimeMs =
                                    SystemClock
                                        .elapsedRealtime(),
                            )
                    }

                    override fun onAccuracyChanged(
                        sensor:
                            Sensor?,
                        accuracy:
                            Int,
                    ) =
                        Unit
                }

            fun register() {
                if (!registered) {
                    registered =
                        sensorManager
                            .registerListener(
                                listener,
                                lightSensor,
                                SensorManager
                                    .SENSOR_DELAY_NORMAL,
                            )
                }
            }

            fun unregister() {
                if (registered) {
                    sensorManager
                        .unregisterListener(
                            listener,
                            lightSensor,
                        )

                    registered =
                        false
                }
            }

            val observer =
                LifecycleEventObserver {
                        _,
                        event ->

                    when (event) {
                        Lifecycle.Event.ON_RESUME ->
                            register()

                        Lifecycle.Event.ON_PAUSE ->
                            unregister()

                        else ->
                            Unit
                    }
                }

            lifecycleOwner
                .lifecycle
                .addObserver(
                    observer
                )

            if (
                lifecycleOwner
                    .lifecycle
                    .currentState
                    .isAtLeast(
                        Lifecycle.State.RESUMED
                    )
            ) {
                register()
            }

            onDispose {
                unregister()

                lifecycleOwner
                    .lifecycle
                    .removeObserver(
                        observer
                    )
            }
        }
    }

    LaunchedEffect(
        navigationActive,
        sensorAvailable,
    ) {
        val systemNight =
            currentSystemNightMode(
                activity
            )

        previousNightMode =
            systemNight

        if (!navigationActive) {
            smoothedLux =
                null

            processedSampleSequence =
                -1L

            presentation =
                NavigationNightPresentation
                    .inactive(
                        systemNightMode =
                            systemNight
                    )

            return@LaunchedEffect
        }

        if (!sensorAvailable) {
            smoothedLux =
                null

            presentation =
                NavigationNightAdaptationPolicy
                    .systemFallback(
                        active =
                            true,
                        systemNightMode =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )

            return@LaunchedEffect
        }

        val now =
            SystemClock.elapsedRealtime()

        val recent =
            latestSample
                ?.takeIf {
                    now -
                        it.elapsedRealtimeMs <=
                        RECENT_LUX_MAX_AGE_MS
                }

        if (recent == null) {
            smoothedLux =
                null

            presentation =
                NavigationNightAdaptationPolicy
                    .awaitingSensor(
                        systemNightMode =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )
        } else {
            smoothedLux =
                recent.lux

            processedSampleSequence =
                recent.sequence

            presentation =
                NavigationNightAdaptationPolicy
                    .fromSensor(
                        smoothedLux =
                            recent.lux,
                        previousNightMode =
                            systemNight,
                        systemNightFallback =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )

            previousNightMode =
                presentation
                    .nightMode
        }
    }

    LaunchedEffect(
        latestSample
            ?.sequence,
        navigationActive,
        brightnessCorrection,
    ) {
        if (!navigationActive) {
            return@LaunchedEffect
        }

        val systemNight =
            currentSystemNightMode(
                activity
            )

        if (!sensorAvailable) {
            presentation =
                NavigationNightAdaptationPolicy
                    .systemFallback(
                        active =
                            true,
                        systemNightMode =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )

            return@LaunchedEffect
        }

        val sample =
            latestSample

        if (
            sample != null &&
            sample.sequence !=
                processedSampleSequence
        ) {
            smoothedLux =
                NavigationNightAdaptationPolicy
                    .smoothLux(
                        previousLux =
                            smoothedLux,
                        sampleLux =
                            sample.lux,
                    )

            processedSampleSequence =
                sample.sequence
        }

        val filtered =
            smoothedLux

        if (filtered == null) {
            presentation =
                NavigationNightAdaptationPolicy
                    .awaitingSensor(
                        systemNightMode =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )
        } else {
            presentation =
                NavigationNightAdaptationPolicy
                    .fromSensor(
                        smoothedLux =
                            filtered,
                        previousNightMode =
                            previousNightMode,
                        systemNightFallback =
                            systemNight,
                        correction =
                            brightnessCorrection,
                    )

            previousNightMode =
                presentation
                    .nightMode
        }
    }

    LaunchedEffect(
        activity,
        navigationActive,
        presentation
            .windowBrightness,
    ) {
        val attributes =
            activity
                .window
                .attributes

        attributes.screenBrightness =
            if (navigationActive) {
                presentation
                    .windowBrightness
                    ?: originalWindowBrightness
            } else {
                originalWindowBrightness
            }

        activity
            .window
            .attributes =
            attributes
    }

    DisposableEffect(
        activity
    ) {
        onDispose {
            val attributes =
                activity
                    .window
                    .attributes

            attributes.screenBrightness =
                originalWindowBrightness

            activity
                .window
                .attributes =
                attributes
        }
    }

    return presentation
}

private fun currentSystemNightMode(
    activity:
        ComponentActivity,
): Boolean {
    val mode =
        activity
            .resources
            .configuration
            .uiMode and
            Configuration
                .UI_MODE_NIGHT_MASK

    return mode ==
        Configuration
            .UI_MODE_NIGHT_YES
}