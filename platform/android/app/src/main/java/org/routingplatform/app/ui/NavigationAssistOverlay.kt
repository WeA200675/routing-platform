package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.routingplatform.app.navigation.NavigationFormatter
import org.routingplatform.app.navigation.NavigationPositionConfidence
import org.routingplatform.app.navigation.NavigationPositionSource
import org.routingplatform.app.navigation.NavigationRawGnssQuality
import org.routingplatform.app.navigation.NavigationRouteEventAhead
import org.routingplatform.app.navigation.NavigationRuntimePipelineStatus
import org.routingplatform.app.navigation.NavigationRouteProgressSafetyStatus
import org.routingplatform.app.navigation.NavigationStartOrientationInfo

@Composable
fun NavigationAssistOverlay(
    criticalEventAhead:
        NavigationRouteEventAhead? =
        null,

    startOrientation:
        NavigationStartOrientationInfo?,

    positionConfidence:
        NavigationPositionConfidence,

    runtimeStatus:
        NavigationRuntimePipelineStatus,

    showRuntimeStatus:
        Boolean,

    modifier:
        Modifier =
        Modifier,

    safetyStatus:
        NavigationRouteProgressSafetyStatus? =
        null,

    lastLocationAccuracyM:
        Double? =
        null,

    rawGnssQuality:
        NavigationRawGnssQuality? =
        null,

    positionSource:
        NavigationPositionSource =
        NavigationPositionSource.Unknown,

    verifiedRadioAnchorCount:
        Int =
        0,

    lastLocationElapsedRealtimeNanos:
        Long? =
        null,
) {
    val renderClockNanos = remember { mutableStateOf(0L) }
    LaunchedEffect(showRuntimeStatus) {
        while (showRuntimeStatus) {
            renderClockNanos.value = SystemClock.elapsedRealtimeNanos()
            delay(1_000L)
        }
    }

    if (
        criticalEventAhead ==
            null &&
        startOrientation ==
            null &&
        !showRuntimeStatus
    ) {
        return
    }

    Surface(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(
                    16.dp
                ),

        tonalElevation =
            6.dp,

        shape =
            MaterialTheme
                .shapes
                .medium,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal =
                        14.dp,

                    vertical =
                        10.dp,
                ),
        ) {
            if (
                startOrientation !=
                    null
            ) {
                Text(
                    text =
                        "Startorientierung",

                    fontWeight =
                        FontWeight.Bold,
                )

                Text(
                    text =
                        "→ Route: " +
                            routeDirectionText(
                                roadRef =
                                    startOrientation
                                        .routeRoadRef,

                                roadName =
                                    startOrientation
                                        .routeRoadName,

                                directionLabel =
                                    startOrientation
                                        .routeDirectionLabel,

                                cardinal =
                                    startOrientation
                                        .routeCardinalDirection,
                            ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                Text(
                    text =
                        "← Gegenrichtung: " +
                            routeDirectionText(
                                roadRef =
                                    startOrientation
                                        .oppositeRoadRef,

                                roadName =
                                    startOrientation
                                        .oppositeRoadName,

                                directionLabel =
                                    startOrientation
                                        .oppositeDirectionLabel,

                                cardinal =
                                    startOrientation
                                        .oppositeCardinalDirection,
                            ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )
            }

            if (
                startOrientation !=
                    null &&
                showRuntimeStatus
            ) {
                Spacer(
                    modifier =
                        Modifier.height(
                            6.dp
                        )
                )
            }

            if (
                showRuntimeStatus
            ) {
                Text(
                    text =
                        "Ortung: " +
                            confidenceText(
                                positionConfidence
                            ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,

                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        runtimeStatusText(
                            runtimeStatus
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                Text(
                    text =
                        "Quelle: " +
                            positionSourceText(positionSource) +
                            fixAgeText(lastLocationElapsedRealtimeNanos, renderClockNanos.value),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )


                if (verifiedRadioAnchorCount > 0) {
                    Text(
                        text = "WLAN-RTT-Anker: $verifiedRadioAnchorCount verifiziert",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Text(
                    text =
                        gnssReceptionLabel(rawGnssQuality),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )

                lastLocationAccuracyM
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { accuracy ->
                        Text(
                            text = "Gemeldete Genauigkeit: ca. " +
                                accuracy.roundToInt() + " m",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                if (
                    runtimeStatus ==
                        NavigationRuntimePipelineStatus.SafetyHold
                ) {
                    Text(
                        text =
                            "Grund: " +
                                safetyHoldReasonText(
                                    safetyStatus
                                ),

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        fontWeight =
                            FontWeight.SemiBold,
                    )

                    gnssReceptionAdvice(
                        rawGnssQuality
                    )?.let { advice ->
                        Text(
                            text =
                                advice,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )
                    }

                }
            }

            if (
                criticalEventAhead !=
                    null
            ) {
                if (
                    startOrientation !=
                        null ||
                    showRuntimeStatus
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )
                }

                Text(
                    text =
                        "Vorausschau",

                    fontWeight =
                        FontWeight.SemiBold,
                )

                Text(
                    text =
                        criticalEventText(
                            criticalEventAhead
                        ),

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                )
            }
        }
    }
}

private fun safetyHoldReasonText(
    status:
        NavigationRouteProgressSafetyStatus?,
): String =
    when (status) {
        NavigationRouteProgressSafetyStatus.HeldLowConfidence ->
            "Positionskonfidenz zu niedrig"

        NavigationRouteProgressSafetyStatus.HeldAmbiguous ->
            "mehrere Routenstellen passen zur Position"

        NavigationRouteProgressSafetyStatus.HeldOffRoute ->
            "GPS-Position liegt zu weit von der Route"

        NavigationRouteProgressSafetyStatus.HeldBackward ->
            "Positionssprung rückwärts wird geprüft"

        NavigationRouteProgressSafetyStatus.HeldForwardJump ->
            "Positionssprung voraus wird geprüft"

        NavigationRouteProgressSafetyStatus.HeldUnconfirmedProgress ->
            "Vorwärtsschritt wartet auf einen zweiten passenden Fix"

        NavigationRouteProgressSafetyStatus.HeldNoEstimate,
        null ->
            "kein gültiger Positionswert"

        NavigationRouteProgressSafetyStatus.Accepted ->
            "Position bestätigt"
    }

private fun criticalEventText(
    eventAhead:
        NavigationRouteEventAhead,
): String {

    val event =
        eventAhead.event

    val instruction =
        event.instruction
            ?.takeIf {
                it.isNotBlank()
            }
            ?: "Manöver voraus"

    val road =
        event.roadName
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                "  $it"
            }
            ?: ""

    return instruction +
        road +
        "  " +
        NavigationFormatter.distance(
            eventAhead.distanceAheadM
        )
}


private fun routeDirectionText(
    roadRef:
        String?,

    roadName:
        String?,

    directionLabel:
        String?,

    cardinal:
        String,
): String {

    val road =
        when {
            !roadRef.isNullOrBlank() ->
                roadRef

            !roadName.isNullOrBlank() ->
                roadName

            else ->
                null
        }

    val direction =
        directionLabel
            ?.takeIf {
                it.isNotBlank()
            }
            ?: germanCardinal(
                cardinal
            )

    return if (
        road !=
            null
    ) {
        "$road · $direction"
    } else {
        direction
    }
}

private fun germanCardinal(
    cardinal:
        String,
): String =
    when (
        cardinal
    ) {
        "N" ->
            "Nord"

        "NE" ->
            "Nordost"

        "E" ->
            "Ost"

        "SE" ->
            "Südost"

        "S" ->
            "Süd"

        "SW" ->
            "Südwest"

        "W" ->
            "West"

        "NW" ->
            "Nordwest"

        else ->
            cardinal
    }

private fun confidenceText(
    confidence:
        NavigationPositionConfidence,
): String =
    when (
        confidence
    ) {
        NavigationPositionConfidence.High ->
            "hoch"

        NavigationPositionConfidence.Medium ->
            "mittel"

        NavigationPositionConfidence.Low ->
            "niedrig"

        NavigationPositionConfidence.Lost ->
            "nicht sicher"
    }

private fun runtimeStatusText(
    status:
        NavigationRuntimePipelineStatus,
): String =
    when (
        status
    ) {
        NavigationRuntimePipelineStatus.Stopped ->
            "Automatische Position inaktiv"

        NavigationRuntimePipelineStatus.Starting ->
            "Sensoren werden gestartet"

        NavigationRuntimePipelineStatus.Running ->
            "Position wird geprüft"

        NavigationRuntimePipelineStatus.NativeProgressUpdated ->
            "Routenposition bestätigt"

        NavigationRuntimePipelineStatus.SafetyHold ->
            "Position unsicher – Fortschritt gehalten"

        NavigationRuntimePipelineStatus.NativeUpdateFailed ->
            "Native Fortschrittsübergabe fehlgeschlagen"
    }
internal fun gnssReceptionLabel(
    quality:
        NavigationRawGnssQuality?,
): String =
    when (quality) {
        NavigationRawGnssQuality.Strong ->
            "GNSS-Empfang: gut"

        NavigationRawGnssQuality.Usable ->
            "GNSS-Empfang: brauchbar"

        NavigationRawGnssQuality.Weak ->
            "GNSS-Empfang: schwach"

        null ->
            "GNSS-Qualität nicht verfügbar"
    }

internal fun gnssReceptionAdvice(
    quality:
        NavigationRawGnssQuality?,
): String? =
    when (quality) {
        NavigationRawGnssQuality.Strong ->
            null

        NavigationRawGnssQuality.Usable ->
            "Freie Sicht zum Himmel verbessert die Positionsstabilität."

        NavigationRawGnssQuality.Weak ->
            "Handy höher und näher an die Frontscheibe setzen; Halterung testweise abnehmen."

        null ->
            "Ortungsfreigabe und freie Sicht zum Himmel prüfen."
    }


private fun positionSourceText(source: NavigationPositionSource): String =
    when (source) {
        NavigationPositionSource.AndroidFused -> "Android-Fusion"
        NavigationPositionSource.Gnss -> "GNSS"
        NavigationPositionSource.Network -> "Netzwerk"
        NavigationPositionSource.VerifiedWifiRtt -> "geprüftes WLAN-RTT"
        NavigationPositionSource.DeadReckoning -> "Koppelnavigation"
        NavigationPositionSource.Unknown -> "unbekannt"
    }

private fun fixAgeText(
    fixElapsedRealtimeNanos: Long?,
    nowElapsedRealtimeNanos: Long,
): String =
    fixElapsedRealtimeNanos
        ?.let {
            " · Fix vor " +
                ((nowElapsedRealtimeNanos - it).coerceAtLeast(0L) / 1_000_000_000.0)
                    .roundToInt() +
                " s"
        }
        ?: " · Fixzeit unbekannt"
