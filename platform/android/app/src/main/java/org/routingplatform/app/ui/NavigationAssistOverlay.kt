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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationPositionConfidence
import org.routingplatform.app.navigation.NavigationRuntimePipelineStatus
import org.routingplatform.app.navigation.NavigationStartOrientationInfo

@Composable
fun NavigationAssistOverlay(
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
) {
    if (
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
            }
        }
    }
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