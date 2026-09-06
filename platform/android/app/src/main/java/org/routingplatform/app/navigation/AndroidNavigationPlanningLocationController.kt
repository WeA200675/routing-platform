package org.routingplatform.app.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

data class NavigationPlanningLocation(
    val position:
        RoutePoint,

    val horizontalAccuracyM:
        Double,
)

interface NavigationPlanningLocationHandle {
    fun cancel()
}

/*
 * One-shot location for route planning while the native navigation
 * session remains in Preview.
 *
 * This controller cannot call JNI, cannot update route progress and
 * requires precise location permission before it starts.
 */
class AndroidNavigationPlanningLocationController(
    context:
        Context,
) :
    AutoCloseable {

    private val appContext =
        context.applicationContext

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private var requestGeneration =
        0L

    private var activeSource:
        AndroidLocationSource? =
        null

    private var activeTimeout:
        Runnable? =
        null

    private var activeCallback:
        (
            (
                Result<NavigationPlanningLocation>
            ) -> Unit
        )? =
        null

    fun request(
        onResult:
            (
                Result<NavigationPlanningLocation>
            ) -> Unit,
    ): NavigationPlanningLocationHandle {

        cancelActive()

        val generation =
            requestGeneration +
                1L

        requestGeneration =
            generation

        if (
            !hasPreciseNavigationLocationPermission(
                appContext
            )
        ) {
            mainHandler.post {
                if (
                    requestGeneration ==
                        generation
                ) {
                    requestGeneration +=
                        1L

                    onResult(
                        Result.failure(
                            NavigationReliabilityException(
                                NavigationReliabilityClassifier
                                    .precisePermissionMissing()
                            )
                        )
                    )
                }
            }

            return handleFor(
                generation
            )
        }

        val source =
            AndroidLocationSource(
                appContext
            )

        activeSource =
            source

        activeCallback =
            onResult

        val timeout =
            Runnable {
                complete(
                    generation =
                        generation,

                    result =
                        Result.failure(
                            NavigationReliabilityException(
                                NavigationReliabilityClassifier
                                    .planningLocationUnavailable()
                            )
                        ),
                )
            }

        activeTimeout =
            timeout

        mainHandler.postDelayed(
            timeout,
            PLANNING_LOCATION_TIMEOUT_MS,
        )

        val started =
            source.start {
                    sample ->

                val accuracy =
                    sample
                        .horizontalAccuracyM

                if (
                    accuracy ==
                        null ||
                    !accuracy.isFinite() ||
                    accuracy >
                        MAX_PLANNING_ACCURACY_M
                ) {
                    return@start
                }

                val ageNanos =
                    SystemClock
                        .elapsedRealtimeNanos() -
                        sample
                            .elapsedRealtimeNanos

                if (
                    ageNanos <
                        0L ||
                    ageNanos >
                        MAX_PLANNING_SAMPLE_AGE_NANOS
                ) {
                    return@start
                }

                complete(
                    generation =
                        generation,

                    result =
                        Result.success(
                            NavigationPlanningLocation(
                                position =
                                    sample.position,

                                horizontalAccuracyM =
                                    accuracy,
                            )
                        ),
                )
            }

        if (
            !started
        ) {
            complete(
                generation =
                    generation,

                result =
                    Result.failure(
                        NavigationReliabilityException(
                            NavigationReliabilityClassifier
                                .planningLocationSourceUnavailable()
                        )
                    ),
            )
        }

        return handleFor(
            generation
        )
    }

    override fun close() {
        cancelActive()
    }

    private fun handleFor(
        generation:
            Long,
    ): NavigationPlanningLocationHandle =
        object :
            NavigationPlanningLocationHandle {

            override fun cancel() {
                if (
                    requestGeneration ==
                        generation
                ) {
                    cancelActive()
                }
            }
        }

    private fun complete(
        generation:
            Long,

        result:
            Result<NavigationPlanningLocation>,
    ) {
        if (
            requestGeneration !=
                generation
        ) {
            return
        }

        val callback =
            activeCallback

        activeSource
            ?.stop()

        activeSource =
            null

        activeTimeout
            ?.let {
                mainHandler.removeCallbacks(
                    it
                )
            }

        activeTimeout =
            null

        activeCallback =
            null

        requestGeneration +=
            1L

        callback
            ?.invoke(
                result
            )
    }

    private fun cancelActive() {
        activeSource
            ?.stop()

        activeSource =
            null

        activeTimeout
            ?.let {
                mainHandler.removeCallbacks(
                    it
                )
            }

        activeTimeout =
            null

        activeCallback =
            null

        requestGeneration +=
            1L
    }
}

private const val MAX_PLANNING_ACCURACY_M =
    100.0

private const val PLANNING_LOCATION_TIMEOUT_MS =
    15_000L

private const val MAX_PLANNING_SAMPLE_AGE_NANOS =
    20_000_000_000L
