package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.NavigationFormatter
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.NavigationControlSide
import kotlin.math.roundToInt

@Composable
fun NavigationScreen(
    snapshot: NavigationUiSnapshot,
    onStartNavigation: () -> Unit,
    onStopNavigation: () -> Unit,
    onAdvanceProgress: () -> Unit,

    navigationStartEnabled:
        Boolean =
        true,

    navigationUnavailableMessage:
        String? =
        null,

    routeAcquisitionMessage:
        String? =
        null,

    manualProgressEnabled:
        Boolean =
        true,

    observedPosition:
        NavigationObservedPositionPresentation? =
        null,

    trustedTravelBearingDegrees:
        Double? =
        null,

    displayPreferences:
        DisplayPreferences =
        DisplayPreferences(),

    onNavigationControlSideChanged:
        (NavigationControlSide) -> Unit =
        {},
) {
    Column(
        modifier =
            Modifier.fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1.0f)
                    .fillMaxWidth(),
        ) {
            RouteMap(
                points =
                    snapshot.geometry,

                shapeSegmentIndex =
                    snapshot.shapeSegmentIndex,

                segmentFraction =
                    snapshot.segmentFraction,

                showProgress =
                    snapshot.state !=
                        NavigationSessionState.Preview,

                observedPosition =
                    observedPosition,

                trustedTravelBearingDegrees =
                    trustedTravelBearingDegrees,

                displayPreferences =
                    displayPreferences,

                modifier =
                    Modifier.fillMaxSize(),
            )

            Surface(
                modifier =
                    Modifier
                        .align(
                            Alignment.TopStart
                        )
                        .statusBarsPadding()
                        .padding(16.dp),

                tonalElevation =
                    6.dp,

                shape =
                    MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp,
                        ),
                ) {
                    Text(
                        text =
                            NavigationFormatter
                                .state(
                                    snapshot.state
                                ),

                        fontWeight =
                            FontWeight.Bold,
                    )

                    Text(
                        text =
                            "Route ${snapshot.routeId}",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )

                    snapshot
                        .engineName
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                                engine ->

                            val version =
                                snapshot
                                    .engineVersion
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {
                                            " $it"
                                        }
                                    ?: ""

                            Text(
                                text =
                                    "Quelle $engine$version",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                            )
                        }
                }
            }
        }

        Surface(
            tonalElevation =
                4.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(18.dp),
            ) {
                if (
                    !routeAcquisitionMessage
                        .isNullOrBlank()
                ) {
                    Text(
                        text =
                            "Routing: " +
                                routeAcquisitionMessage,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,

                        fontWeight =
                            FontWeight.Medium,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )
                }

                Text(
                    text =
                        snapshot
                            .currentManeuver
                            ?.instruction
                            ?: "Route bereit",

                    style =
                        MaterialTheme
                            .typography
                            .titleLarge,

                    fontWeight =
                        FontWeight.SemiBold,
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                ) {
                    InfoValue(
                        label =
                            "Reststrecke",

                        value =
                            NavigationFormatter
                                .distance(
                                    snapshot
                                        .remainingDistanceM
                                ),
                    )

                    InfoValue(
                        label =
                            "Restzeit",

                        value =
                            NavigationFormatter
                                .duration(
                                    snapshot
                                        .remainingDurationS
                                ),
                    )

                    InfoValue(
                        label =
                            "Fortschritt",

                        value =
                            (
                                snapshot
                                    .progressFraction *
                                    100.0
                            )
                                .roundToInt()
                                .toString() +
                                " %",
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )

                val navigationControl =
                    NavigationControlPresentation
                        .create(
                            state =
                                snapshot.state,

                            side =
                                displayPreferences
                                    .navigationControlSide,

                            navigationStartEnabled =
                                navigationStartEnabled,
                        )

                NavigationPrimaryControl(
                    presentation =
                        navigationControl,

                    onPrimaryAction = {
                        when (
                            snapshot.state
                        ) {
                            NavigationSessionState.Preview ->
                                onStartNavigation()

                            NavigationSessionState.Navigating ->
                                onStopNavigation()

                            NavigationSessionState.Arrived ->
                                Unit
                        }
                    },

                    onMoveToOtherSide = {
                        onNavigationControlSideChanged(
                            navigationControl
                                .oppositeSide()
                        )
                    },
                )

                when (
                    snapshot.state
                ) {
                    NavigationSessionState.Preview -> {
                        if (
                            !navigationStartEnabled &&
                            !navigationUnavailableMessage
                                .isNullOrBlank()
                        ) {
                            Spacer(
                                modifier =
                                    Modifier.height(
                                        8.dp
                                    )
                            )

                            Text(
                                text =
                                    navigationUnavailableMessage,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .error,

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                            )
                        }
                    }

                    NavigationSessionState.Navigating -> {
                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Button(
                            modifier =
                                Modifier.fillMaxWidth(),

                            enabled =
                                manualProgressEnabled,

                            onClick =
                                onAdvanceProgress,
                        ) {
                            Text(
                                if (
                                    manualProgressEnabled
                                ) {
                                    "Diagnose: Fortschritt weiter"
                                } else {
                                    "Automatische Position aktiv"
                                }
                            )
                        }
                    }

                    NavigationSessionState.Arrived ->
                        Unit
                }

                if (
                    !snapshot
                        .presentationBoundaryIntact
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Safety boundary violation",

                        color =
                            MaterialTheme
                                .colorScheme
                                .error,
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationPrimaryControl(
    presentation:
        NavigationControlPresentation,

    onPrimaryAction:
        () -> Unit,

    onMoveToOtherSide:
        () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),

        horizontalArrangement =
            when (
                presentation.side
            ) {
                NavigationControlSide.Left ->
                    Arrangement.Start

                NavigationControlSide.Right ->
                    Arrangement.End
            },
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth(
                    0.72f
                ),

            horizontalAlignment =
                when (
                    presentation.side
                ) {
                    NavigationControlSide.Left ->
                        Alignment.Start

                    NavigationControlSide.Right ->
                        Alignment.End
                },
        ) {
            Button(
                modifier =
                    Modifier.fillMaxWidth(),

                enabled =
                    presentation.enabled,

                colors =
                    if (
                        presentation.destructive
                    ) {
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .error,

                                contentColor =
                                    MaterialTheme
                                        .colorScheme
                                        .onError,
                            )
                    } else {
                        ButtonDefaults
                            .buttonColors()
                    },

                onClick =
                    onPrimaryAction,
            ) {
                Text(
                    presentation.label
                )
            }

            if (
                presentation.enabled
            ) {
                TextButton(
                    onClick =
                        onMoveToOtherSide,
                ) {
                    Text(
                        when (
                            presentation.side
                        ) {
                            NavigationControlSide.Left ->
                                "Nach rechts verschieben"

                            NavigationControlSide.Right ->
                                "Nach links verschieben"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoValue(
    label: String,
    value: String,
) {
    Column {
        Text(
            text =
                value,

            style =
                MaterialTheme
                    .typography
                    .titleMedium,

            fontWeight =
                FontWeight.Bold,
        )

        Text(
            text =
                label,

            style =
                MaterialTheme
                    .typography
                    .bodySmall,
        )
    }
}
