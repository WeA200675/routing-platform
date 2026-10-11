package org.routingplatform.app.navigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import org.routingplatform.app.R
import org.routingplatform.app.places.AndroidFavoriteDestinationStore

/*
 * Debug-build-only fault probe.
 *
 * This Activity is declared only in src/debug/AndroidManifest.xml.
 * It can observe/reject injected fault conditions but cannot advance
 * route progress, fabricate positioning truth, bypass permissions, or
 * weaken JNI/session boundaries.
 */
class G5R5FaultProbeActivity :
    Activity() {

    override fun onCreate(
        savedInstanceState:
            Bundle?,
    ) {
        super.onCreate(
            savedInstanceState
        )

        val statusView =
            TextView(
                this
            ).apply {
                id =
                    R.id.g5r5_probe_status

                textSize =
                    16.0f

                setPadding(
                    32,
                    32,
                    32,
                    32,
                )
            }

        setContentView(
            statusView
        )

        val mode =
            intent
                .getStringExtra(
                    EXTRA_MODE
                )
                ?.trim()
                .orEmpty()

        val outcome =
            runCatching {
                runProbe(
                    mode
                )
            }

        statusView.text =
            outcome.fold(
                onSuccess = {
                        detail ->

                    "PASS|$mode|$detail"
                },

                onFailure = {
                        error ->

                    val message =
                        (
                            error.message
                                ?: error.javaClass.simpleName
                        )
                            .replace(
                                '|',
                                '/'
                            )
                            .take(
                                240
                            )

                    "FAIL|$mode|" +
                        error.javaClass.simpleName +
                        "|" +
                        message
                },
            )
    }

    private fun runProbe(
        mode:
            String,
    ): String =
        when (
            mode
        ) {
            "geocoder_unavailable" ->
                probeDestinationSearchFault(
                    "Android Geocoder unavailable"
                )

            "geocoder_lookup_failure" ->
                probeDestinationSearchFault(
                    "Android Geocoder lookup failed"
                )

            "persistence_corrupt_read" ->
                probeCorruptFavoriteRead()

            "persistence_write_retry" ->
                probePersistenceWriteRetry()

            "map_failure" ->
                probeMapFailurePolicy()

            "planning_stale" ->
                probeStalePlanningLocation()

            "permission_state" ->
                probePermissionState()

            "permission_missing" ->
                probePrecisePermissionMissing()

            "stale_callback" ->
                probeStaleCallback()

            "jni_illegal" ->
                probeIllegalJniTransition()

            else ->
                error(
                    "Unsupported G5R5 probe mode: $mode"
                )
        }

    private fun probeDestinationSearchFault(
        detail:
            String,
    ): String {

        val fault =
            NavigationReliabilityClassifier
                .destinationSearchUnavailable(
                    detail
                )

        check(
            fault.code ==
                NavigationFaultCode.DestinationSearchUnavailable
        )

        check(
            fault.domain ==
                NavigationFaultDomain.DestinationSearch
        )

        check(
            fault.disposition ==
                NavigationFaultDisposition.RetryableInfrastructure
        )

        return "DestinationSearchUnavailable/RetryableInfrastructure"
    }

    private fun probeCorruptFavoriteRead():
        String {

        val profileId =
            "g5r5-probe"

        val preferences =
            applicationContext
                .getSharedPreferences(
                    FAVORITE_PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                )

        val key =
            "profile:" +
                profileId

        val hadPrevious =
            preferences.contains(
                key
            )

        val previous =
            if (
                hadPrevious
            ) {
                preferences.getString(
                    key,
                    null,
                )
            } else {
                null
            }

        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        try {
            check(
                preferences
                    .edit()
                    .putString(
                        key,
                        "g5r5-intentionally-corrupt",
                    )
                    .commit()
            )

            val store =
                AndroidFavoriteDestinationStore(
                    context =
                        applicationContext,

                    onReliabilityEvent =
                        events::add,
                )

            val loaded =
                store.load(
                    profileId
                )

            check(
                loaded.profileId ==
                    profileId
            )

            check(
                loaded.all()
                    .isEmpty()
            )

            check(
                events.any {
                    it.kind ==
                        NavigationReliabilityEventKind.FaultObserved &&
                        it.fault.code ==
                        NavigationFaultCode.PersistenceUnavailable
                }
            )

            check(
                events.any {
                    it.kind ==
                        NavigationReliabilityEventKind.RecoveryExhausted &&
                        it.fault.code ==
                        NavigationFaultCode.PersistenceUnavailable
                }
            )

            return "fallback-empty/PersistenceUnavailable"
        }
        finally {
            val editor =
                preferences.edit()

            if (
                hadPrevious
            ) {
                editor.putString(
                    key,
                    previous,
                )
            } else {
                editor.remove(
                    key
                )
            }

            check(
                editor.commit()
            )
        }
    }

    private fun probePersistenceWriteRetry():
        String {

        var attempts =
            0

        val events =
            mutableListOf<
                NavigationReliabilityEvent
            >()

        val saved =
            NavigationPersistenceReliability
                .saveWithSingleRetry(
                    operationName =
                        "g5r5.device.write-failure",

                    onReliabilityEvent =
                        events::add,
                ) {
                    attempts +=
                        1

                    false
                }

        check(
            !saved
        )

        check(
            attempts ==
                2
        )

        check(
            events.count {
                it.kind ==
                    NavigationReliabilityEventKind.RecoveryScheduled
            } ==
                1
        )

        check(
            events.last()
                .kind ==
                NavigationReliabilityEventKind.RecoveryExhausted
        )

        return "attempts=2/retries=1"
    }

    private fun probeMapFailurePolicy():
        String {

        val fault =
            NavigationReliabilityClassifier
                .mapUnavailable(
                    "g5r5 device map presentation fault"
                )

        val policy =
            NavigationRecoveryPolicy()

        val first =
            policy.decide(
                fault =
                    fault,

                retriesAlreadyAttempted =
                    0,
            )

        check(
            first is
                NavigationRecoveryDecision.Retry
        )

        first as
            NavigationRecoveryDecision.Retry

        check(
            first.retryNumber ==
                1
        )

        val second =
            policy.decide(
                fault =
                    fault,

                retriesAlreadyAttempted =
                    1,
            )

        check(
            second ===
                NavigationRecoveryDecision.FailClosed
        )

        return "retry=1/then-fail-closed"
    }

    private fun probeStalePlanningLocation():
        String {

        val gate =
            NavigationPlanningLocationGate()

        val stale =
            NavigationLocationSample(
                position =
                    RoutePoint(
                        latitude =
                            47.1410,

                        longitude =
                            9.5209,
                    ),

                horizontalAccuracyM =
                    5.0,

                elapsedRealtimeNanos =
                    1_000_000_000L,

                provider =
                    "g5r5-probe",
            )

        val accepted =
            gate.accept(
                sample =
                    stale,

                nowElapsedRealtimeNanos =
                    21_000_000_001L,
            )

        check(
            accepted ==
                null
        )

        return "stale-sample-rejected"
    }

    private fun probePermissionState():
        String {

        val fine =
            if (
                checkSelfPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                "granted"
            } else {
                "denied"
            }

        val coarse =
            if (
                checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                "granted"
            } else {
                "denied"
            }

        return "fine=$fine;coarse=$coarse"
    }

    private fun probePrecisePermissionMissing():
        String {

        val preciseGranted =
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        check(
            !preciseGranted
        ) {
            "ACCESS_FINE_LOCATION is still granted inside the probe process."
        }

        val fault =
            NavigationReliabilityClassifier
                .precisePermissionMissing()

        check(
            fault.code ==
                NavigationFaultCode.PermissionMissing
        ) {
            "Missing precise permission was not classified as PermissionMissing."
        }

        check(
            fault.disposition ==
                NavigationFaultDisposition.FailClosedNavigationTruth
        ) {
            "Missing precise permission did not fail closed."
        }

        return "fine-denied/PermissionMissing"
    }

    private fun probeStaleCallback():
        String {

        val gate =
            NavigationAsyncGenerationGate()

        val oldToken =
            gate.begin()

        val currentToken =
            gate.begin()

        check(
            !gate.isCurrent(
                oldToken
            )
        )

        check(
            !gate.consume(
                oldToken
            )
        )

        check(
            gate.consume(
                currentToken
            )
        )

        check(
            !gate.consume(
                currentToken
            )
        )

        return "older-generation-ignored"
    }

    private fun probeIllegalJniTransition():
        String {

        val error =
            runCatching {
                requireNavigatingRouteReplacementState(
                    NavigationSessionState.Preview
                )
            }
                .exceptionOrNull()

        check(
            error is
                NavigationReliabilityException
        )

        error as
            NavigationReliabilityException

        check(
            error.fault.code ==
                NavigationFaultCode.NativeBoundaryRejected
        )

        check(
            error.fault.disposition ==
                NavigationFaultDisposition.FailClosedNavigationTruth
        )

        return "Preview-replacement-rejected"
    }

    companion object {
        private const val EXTRA_MODE =
            "mode"

        private const val FAVORITE_PREFERENCES_NAME =
            "routing-platform-favorite-destinations-v1"
    }
}
