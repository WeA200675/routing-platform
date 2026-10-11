package org.routingplatform.app.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

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

    private val planningLocationGate =
        NavigationPlanningLocationGate()

    private val generationGate =
        NavigationAsyncGenerationGate()

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
            generationGate
                .begin()

        if (
            !hasPreciseNavigationLocationPermission(
                appContext
            )
        ) {
            mainHandler.post {
                if (
                    generationGate
                        .consume(
                            generation
                        )
                ) {
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

                val planningLocation =
                    planningLocationGate
                        .accept(
                            sample =
                                sample,

                            nowElapsedRealtimeNanos =
                                SystemClock
                                    .elapsedRealtimeNanos(),
                        )
                        ?: return@start

                complete(
                    generation =
                        generation,

                    result =
                        Result.success(
                            planningLocation
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
                    generationGate
                        .isCurrent(
                            generation
                        )
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
            !generationGate
                .consume(
                    generation
                )
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

        generationGate
            .cancel()
    }
}

private const val PLANNING_LOCATION_TIMEOUT_MS =
    15_000L
