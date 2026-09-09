package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.navigation.NavigationFaultCode
import org.routingplatform.app.navigation.NavigationFormatter
import org.routingplatform.app.navigation.NavigationRouteAcquisitionState
import org.routingplatform.app.navigation.NavigationSessionState
import org.routingplatform.app.navigation.NavigationTripPlan
import org.routingplatform.app.navigation.NavigationTripStop
import org.routingplatform.app.navigation.NavigationUiSnapshot
import org.routingplatform.app.navigation.RoutePoint
import org.routingplatform.app.places.DestinationSearchResult
import org.routingplatform.app.places.FavoriteDestination
import org.routingplatform.app.places.FavoriteDestinationCollection
import org.routingplatform.app.profile.DisplayPreferences
import org.routingplatform.app.profile.ExperiencePackCatalog
import org.routingplatform.app.profile.ExperiencePackRuntimeResolver
import org.routingplatform.app.profile.NavigationControlSide
import org.routingplatform.app.profile.NavigationHapticIntensity
import org.routingplatform.app.profile.NavigationPersonalityPreferences
import org.routingplatform.app.profile.NavigationPreferences
import org.routingplatform.app.profile.VoicePreferences
import org.routingplatform.app.profile.WeeklyDiscoveryIntensity
import kotlin.math.roundToInt

@Composable
internal fun NavigationScreen(
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

    routeAcquisitionState:
        NavigationRouteAcquisitionState =
        NavigationRouteAcquisitionState.FallbackReady,

    routeAcquisitionFaultCode:
        NavigationFaultCode? =
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

    nightPresentation:
        NavigationNightPresentation =
        NavigationNightPresentation
            .inactive(),

    onNightBrightnessDarker:
        () -> Unit =
        {},

    onNightBrightnessBrighter:
        () -> Unit =
        {},

    displayPreferences:
        DisplayPreferences =
        DisplayPreferences(),

    onNavigationControlSideChanged:
        (NavigationControlSide) -> Unit =
        {},

    personalityPreferences:
        NavigationPersonalityPreferences =
        NavigationPersonalityPreferences(),

    voicePreferences:
        VoicePreferences =
        VoicePreferences(),

    voiceCatalogState:
        NavigationVoiceCatalogState =
        NavigationVoiceCatalogState
            .Initializing,

    onVoiceCatalogRefresh:
        () -> Unit =
        {},

    onVoicePreview:
        (VoicePreferences) ->
        NavigationVoicePreviewResult = {
            NavigationVoicePreviewResult
                .NotReady
        },

    onVoicePreferencesChanged:
        (VoicePreferences) -> Unit =
        {},

    navigationPreferences:
        NavigationPreferences =
        NavigationPreferences(),

    hapticAvailable:
        Boolean =
        false,

    onHapticPreview:
        (NavigationHapticIntensity) ->
        Boolean =
        {
            false
        },

    onNavigationPreferencesChanged:
        (NavigationPreferences) -> Unit =
        {},

    onExperiencePackSelected:
        (String) -> Unit =
        {},

    onWeeklyDiscoveryChanged:
        (Boolean) -> Unit =
        {},

    onWeeklyDiscoveryIntensityChanged:
        (WeeklyDiscoveryIntensity) -> Unit =
        {},

    selectedTripStop:
        NavigationTripStop? =
        null,

    tripPlan:
        NavigationTripPlan? =
        null,

    favoriteDestinations:
        FavoriteDestinationCollection? =
        null,

    destinationSearchResults:
        List<DestinationSearchResult> =
        emptyList(),

    destinationPlannerMessage:
        String? =
        null,

    destinationPlannerBusy:
        Boolean =
        false,

    onMapTargetSelected:
        (RoutePoint) -> Unit =
        {},

    onSearchDestination:
        (String) -> Unit =
        {},

    onSearchResultSelected:
        (DestinationSearchResult) -> Unit =
        {},

    onFavoriteSelected:
        (FavoriteDestination) -> Unit =
        {},

    onSaveSelectedAsHome:
        () -> Unit =
        {},

    onSaveSelectedAsWork:
        () -> Unit =
        {},

    onSaveSelectedAsCustom:
        (String) -> Unit =
        {},

    onDeleteFavorite:
        (String) -> Unit =
        {},

    onUseSelectedAsDestination:
        () -> Unit =
        {},

    onAppendSelectedVia:
        () -> Unit =
        {},

    onClearSelectedTarget:
        () -> Unit =
        {},

    onRemoveVia:
        (Int) -> Unit =
        {},

    onMoveViaUp:
        (Int) -> Unit =
        {},

    onMoveViaDown:
        (Int) -> Unit =
        {},
) {
    var plannerOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var experiencePackOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var voiceSettingsOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var hapticSettingsOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var criticalGuidanceSettingsOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var cameraZoomSettingsOpen by
        remember {
            mutableStateOf(
                false
            )
        }

    var searchQuery by
        remember {
            mutableStateOf(
                ""
            )
        }

    var customFavoriteLabel by
        remember {
            mutableStateOf(
                ""
            )
        }

    val focusMode =
        rememberNavigationFocusMode(
            navigationActive =
                snapshot.state ==
                    NavigationSessionState
                        .Navigating,
        )

    val runtimeDisplayPreferences =
        ExperiencePackRuntimeResolver
            .resolveDisplayPreferences(
                base =
                    displayPreferences,

                personality =
                    personalityPreferences,
            )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .semantics {
                    /*
                     * Expose testTag as resource-id to UIAutomator.
                     * This is automation metadata only.
                     */
                    testTagsAsResourceId =
                        true
                }
                .testTag(
                    NavigationUiTestTags.Root
                ),
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
                    runtimeDisplayPreferences,

                automaticNightMode =
                    nightPresentation
                        .active &&
                        nightPresentation
                            .nightMode,

                automaticMapZoomEnabled =
                    navigationPreferences
                        .automaticMapZoom &&
                        snapshot.state ==
                            NavigationSessionState
                            .Navigating,

                distanceToCurrentManeuverEndM =
                    snapshot
                        .distanceToCurrentManeuverEndM,

                focusModeActive =
                    focusMode
                        .presentation
                        .focused,

                onMapTap =
                    focusMode
                        .notifyUserActivity,

                onMapInteractionChanged =
                    focusMode
                        .setInteractionActive,

                selectedTarget =
                    selectedTripStop
                        ?.point,

                mapSelectionEnabled =
                    snapshot.state ==
                        NavigationSessionState.Preview &&
                        !destinationPlannerBusy,

                onMapLongPress = {
                        point ->

                    onMapTargetSelected(
                        point
                    )

                    plannerOpen =
                        true
                },

                modifier =
                    Modifier.fillMaxSize(),
            )

            if (
                focusMode
                    .presentation
                    .showTopChrome
            ) {
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

                        modifier =
                            Modifier.testTag(
                                NavigationUiTestTags
                                    .sessionState(
                                        snapshot.state
                                    )
                            ),

                        fontWeight =
                            FontWeight.Bold,
                    )

                    Text(
                        text =
                            "Route ${snapshot.routeId}",

                        modifier =
                            Modifier.testTag(
                                NavigationUiTestTags.RouteId
                            ),

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

                        modifier =
                            Modifier.testTag(
                                NavigationUiTestTags
                                    .routeAcquisition(
                                        state =
                                            routeAcquisitionState,

                                        faultCode =
                                            routeAcquisitionFaultCode,
                                    )
                            ),

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
                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Button(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .PlannerOpen
                                    ),

                            enabled =
                                !destinationPlannerBusy,

                            onClick = {
                                plannerOpen =
                                    true
                            },
                        ) {
                            Text(
                                if (
                                    destinationPlannerBusy
                                ) {
                                    "Zielplanung läuft …"
                                } else {
                                    "Ziel planen"
                                }
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .ExperiencePackOpen
                                    ),

                            onClick = {
                                experiencePackOpen =
                                    true
                            },
                        ) {
                            val activePack =
                                ExperiencePackRuntimeResolver
                                    .resolvePackOverride(
                                        personality =
                                            personalityPreferences
                                    )
                                    ?: ExperiencePackCatalog
                                        .require(
                                            personalityPreferences
                                                .selectedPackId
                                        )

                            Text(
                                text =
                                    "Navi-Stil: " +
                                        activePack
                                            .displayName +
                                        if (
                                            personalityPreferences
                                                .weeklyDiscoveryEnabled
                                        ) {
                                            " · Überraschung an"
                                        } else {
                                            ""
                                        }
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .VoiceSettingsOpen
                                    ),

                            onClick = {
                                onVoiceCatalogRefresh()

                                voiceSettingsOpen =
                                    true
                            },
                        ) {
                            Text(
                                text =
                                    "Sprachführung: " +
                                        if (
                                            voicePreferences
                                                .enabled
                                        ) {
                                            NavigationVoiceCatalog
                                                .languageDisplayName(
                                                    voicePreferences
                                                        .languageTag
                                                )
                                        } else {
                                            "Aus | " +
                                                NavigationVoiceCatalog
                                                    .languageDisplayName(
                                                        voicePreferences
                                                            .languageTag
                                                    )
                                        }
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .CameraZoomSettingsOpen
                                    ),

                            onClick = {
                                cameraZoomSettingsOpen =
                                    true
                            },
                        ) {
                            Text(
                                text =
                                    "Kartenzoom: " +
                                        if (
                                            navigationPreferences
                                                .automaticMapZoom
                                        ) {
                                            "Automatisch"
                                        } else {
                                            "Fest"
                                        }
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .CriticalGuidanceSettingsOpen
                                    ),

                            onClick = {
                                criticalGuidanceSettingsOpen =
                                    true
                            },
                        ) {
                            Text(
                                text =
                                    "Wichtige Hinweise: " +
                                        if (
                                            navigationPreferences
                                                .repeatCriticalInstructions
                                        ) {
                                            "Wiederholen"
                                        } else {
                                            "Einmal"
                                        }
                            )
                        }

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .HapticSettingsOpen
                                    ),

                            onClick = {
                                hapticSettingsOpen =
                                    true
                            },
                        ) {
                            Text(
                                text =
                                    "Haptik: " +
                                        if (
                                            navigationPreferences
                                                .hapticGuidanceEnabled
                                        ) {
                                            navigationHapticIntensityLabel(
                                                navigationPreferences
                                                    .hapticIntensity
                                            )
                                        } else {
                                            "Aus | " +
                                                navigationHapticIntensityLabel(
                                                    navigationPreferences
                                                        .hapticIntensity
                                                )
                                        }
                            )
                        }

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
                        if (
                            focusMode
                                .presentation
                                .showSecondaryControls
                        ) {
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

                            onClick = {
                                focusMode
                                    .notifyUserActivity()

                                onAdvanceProgress()
                            },
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

                        Spacer(
                            modifier =
                                Modifier.height(
                                    8.dp
                                )
                        )

                        Surface(
                            tonalElevation =
                                2.dp,

                            shape =
                                MaterialTheme
                                    .shapes
                                    .medium,
                        ) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal =
                                                12.dp,

                                            vertical =
                                                8.dp,
                                        ),
                            ) {
                                Text(
                                    text =
                                        nightPresentation
                                            .statusText(),

                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,

                                    fontWeight =
                                        FontWeight.Medium,
                                )

                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(),

                                    horizontalArrangement =
                                        Arrangement
                                            .SpaceBetween,
                                ) {
                                    TextButton(
                                        enabled =
                                            nightPresentation
                                                .brightnessCorrection >
                                                NAVIGATION_BRIGHTNESS_CORRECTION_MIN,

                                        onClick = {
                                            focusMode
                                                .notifyUserActivity()

                                            onNightBrightnessDarker()
                                        },
                                    ) {
                                        Text(
                                            "Dunkler"
                                        )
                                    }

                                    TextButton(
                                        enabled =
                                            nightPresentation
                                                .brightnessCorrection <
                                                NAVIGATION_BRIGHTNESS_CORRECTION_MAX,

                                        onClick = {
                                            focusMode
                                                .notifyUserActivity()

                                            onNightBrightnessBrighter()
                                        },
                                    ) {
                                        Text(
                                            "Heller"
                                        )
                                    }
                                }
                            }
                        }
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

    if (
        plannerOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        DestinationPlannerDialog(
            selectedTripStop =
                selectedTripStop,

            tripPlan =
                tripPlan,

            favoriteDestinations =
                favoriteDestinations,

            searchResults =
                destinationSearchResults,

            plannerMessage =
                destinationPlannerMessage,

            busy =
                destinationPlannerBusy,

            searchQuery =
                searchQuery,

            onSearchQueryChanged = {
                searchQuery =
                    it
            },

            customFavoriteLabel =
                customFavoriteLabel,

            onCustomFavoriteLabelChanged = {
                customFavoriteLabel =
                    it
            },

            onDismiss = {
                plannerOpen =
                    false
            },

            onSearch = {
                onSearchDestination(
                    searchQuery
                )
            },

            onSearchResultSelected =
                onSearchResultSelected,

            onFavoriteSelected =
                onFavoriteSelected,

            onSaveHome =
                onSaveSelectedAsHome,

            onSaveWork =
                onSaveSelectedAsWork,

            onSaveCustom = {
                onSaveSelectedAsCustom(
                    customFavoriteLabel
                )
            },

            onDeleteFavorite =
                onDeleteFavorite,

            onUseAsDestination = {
                onUseSelectedAsDestination()

                plannerOpen =
                    false
            },

            onAppendVia =
                onAppendSelectedVia,

            onClearSelection =
                onClearSelectedTarget,

            onRemoveVia =
                onRemoveVia,

            onMoveViaUp =
                onMoveViaUp,

            onMoveViaDown =
                onMoveViaDown,
        )
    }

    if (
        experiencePackOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        ExperiencePackDialog(
            preferences =
                personalityPreferences,

            onSelectPack =
                onExperiencePackSelected,

            onWeeklyDiscoveryChanged =
                onWeeklyDiscoveryChanged,

            onWeeklyDiscoveryIntensityChanged =
                onWeeklyDiscoveryIntensityChanged,

            onDismiss = {
                experiencePackOpen =
                    false
            },
        )
    }

    if (
        voiceSettingsOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        NavigationVoiceSettingsDialog(
            preferences =
                voicePreferences,

            personalityPreferences =
                personalityPreferences,

            catalogState =
                voiceCatalogState,

            onPreview =
                onVoicePreview,

            onSave =
                onVoicePreferencesChanged,

            onDismiss = {
                voiceSettingsOpen =
                    false
            },
        )
    }

    if (
        cameraZoomSettingsOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        NavigationCameraZoomSettingsDialog(
            preferences =
                navigationPreferences,

            onSave =
                onNavigationPreferencesChanged,

            onDismiss = {
                cameraZoomSettingsOpen =
                    false
            },
        )
    }

    if (
        criticalGuidanceSettingsOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        NavigationCriticalGuidanceSettingsDialog(
            preferences =
                navigationPreferences,

            onSave =
                onNavigationPreferencesChanged,

            onDismiss = {
                criticalGuidanceSettingsOpen =
                    false
            },
        )
    }

    if (
        hapticSettingsOpen &&
        snapshot.state ==
            NavigationSessionState.Preview
    ) {
        NavigationHapticSettingsDialog(
            preferences =
                navigationPreferences,

            hapticAvailable =
                hapticAvailable,

            onPreview =
                onHapticPreview,

            onSave =
                onNavigationPreferencesChanged,

            onDismiss = {
                hapticSettingsOpen =
                    false
            },
        )
    }
}

@Composable
private fun DestinationPlannerDialog(
    selectedTripStop:
        NavigationTripStop?,

    tripPlan:
        NavigationTripPlan?,

    favoriteDestinations:
        FavoriteDestinationCollection?,

    searchResults:
        List<DestinationSearchResult>,

    plannerMessage:
        String?,

    busy:
        Boolean,

    searchQuery:
        String,

    onSearchQueryChanged:
        (String) -> Unit,

    customFavoriteLabel:
        String,

    onCustomFavoriteLabelChanged:
        (String) -> Unit,

    onDismiss:
        () -> Unit,

    onSearch:
        () -> Unit,

    onSearchResultSelected:
        (DestinationSearchResult) -> Unit,

    onFavoriteSelected:
        (FavoriteDestination) -> Unit,

    onSaveHome:
        () -> Unit,

    onSaveWork:
        () -> Unit,

    onSaveCustom:
        () -> Unit,

    onDeleteFavorite:
        (String) -> Unit,

    onUseAsDestination:
        () -> Unit,

    onAppendVia:
        () -> Unit,

    onClearSelection:
        () -> Unit,

    onRemoveVia:
        (Int) -> Unit,

    onMoveViaUp:
        (Int) -> Unit,

    onMoveViaDown:
        (Int) -> Unit,
) {
    val focusManager =
        LocalFocusManager.current

    Dialog(
        onDismissRequest =
            onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.92f
                    )
                    .imePadding()
                    .testTag(
                        NavigationUiTestTags
                            .PlannerDialog
                    ),

            tonalElevation =
                8.dp,

            shape =
                MaterialTheme
                    .shapes
                    .large,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(
                            16.dp
                        ),
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            "Zielplanung",

                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,

                        fontWeight =
                            FontWeight.Bold,
                    )

                    TextButton(
                        onClick =
                            onDismiss,
                    ) {
                        Text(
                            "Schließen"
                        )
                    }
                }

                plannerMessage
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                            message ->

                        Text(
                            text =
                                message,

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
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
                        "Ziel suchen",

                    fontWeight =
                        FontWeight.SemiBold,
                )

                OutlinedTextField(
                    value =
                        searchQuery,

                    onValueChange =
                        onSearchQueryChanged,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .SearchField
                            ),

                    enabled =
                        !busy,

                    singleLine =
                        true,

                    keyboardOptions =
                        KeyboardOptions(
                            imeAction =
                                ImeAction.Search
                        ),

                    keyboardActions =
                        KeyboardActions(
                            onSearch = {
                                if (
                                    !busy &&
                                    searchQuery
                                        .trim()
                                        .length in
                                        2..160
                                ) {
                                    focusManager
                                        .clearFocus()

                                    onSearch()
                                }
                            }
                        ),

                    label = {
                        Text(
                            "Adresse, Ort oder POI"
                        )
                    },
                )

                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp
                        )
                )

                Button(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                NavigationUiTestTags
                                    .SearchAction
                            ),

                    enabled =
                        !busy &&
                        searchQuery
                            .trim()
                            .length in
                            2..160,

                    onClick = {
                        focusManager
                            .clearFocus()

                        onSearch()
                    },
                ) {
                    Text(
                        "Suchen"
                    )
                }

                if (
                    searchResults.isNotEmpty()
                ) {
                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    searchResults.forEachIndexed {
                            index,
                            result ->

                        TextButton(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag(
                                        NavigationUiTestTags
                                            .searchResult(
                                                index
                                            )
                                    ),

                            enabled =
                                !busy,

                            onClick = {
                                onSearchResultSelected(
                                    result
                                )
                            },
                        ) {
                            Text(
                                result.displayText
                            )
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        "Favoriten",

                    fontWeight =
                        FontWeight.SemiBold,
                )

                val favorites =
                    favoriteDestinations
                        ?.all()
                        ?: emptyList()

                if (
                    favorites.isEmpty()
                ) {
                    Text(
                        text =
                            "Noch keine Favoriten gespeichert.",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )
                } else {
                    favorites.forEach {
                            favorite ->

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically,
                        ) {
                            TextButton(
                                modifier =
                                    Modifier.weight(
                                        1.0f
                                    ),

                                enabled =
                                    !busy,

                                onClick = {
                                    onFavoriteSelected(
                                        favorite
                                    )
                                },
                            ) {
                                Text(
                                    favorite.label
                                )
                            }

                            TextButton(
                                enabled =
                                    !busy,

                                onClick = {
                                    onDeleteFavorite(
                                        favorite.id
                                    )
                                },
                            ) {
                                Text(
                                    "Löschen"
                                )
                            }
                        }
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(
                            12.dp
                        )
                )

                Text(
                    text =
                        "Ausgewählte Markierung",

                    fontWeight =
                        FontWeight.SemiBold,
                )

                if (
                    selectedTripStop ==
                        null
                ) {
                    Text(
                        text =
                            "Karte lange drücken oder einen Suchtreffer/Favoriten auswählen.",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )
                } else {
                    Text(
                        text =
                            selectedTripStop
                                .label
                                ?: "Kartenpunkt",

                        fontWeight =
                            FontWeight.Medium,
                    )

                    Text(
                        text =
                            selectedTripStop
                                .point
                                .latitude
                                .toString() +
                                ", " +
                                selectedTripStop
                                    .point
                                    .longitude
                                    .toString(),

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                8.dp
                            )
                    )

                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .UseDestination
                                ),

                        enabled =
                            !busy,

                        onClick =
                            onUseAsDestination,
                    ) {
                        Text(
                            "Hierhin navigieren"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .AppendVia
                                ),

                        enabled =
                            !busy &&
                            (
                                tripPlan
                                    ?.viaPoints
                                    ?.size
                                    ?: 16
                            ) <
                                16,

                        onClick =
                            onAppendVia,
                    ) {
                        Text(
                            "Als Zwischenziel"
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            enabled =
                                !busy,

                            onClick =
                                onSaveHome,
                        ) {
                            Text(
                                "Als Zuhause"
                            )
                        }

                        TextButton(
                            enabled =
                                !busy,

                            onClick =
                                onSaveWork,
                        ) {
                            Text(
                                "Als Arbeit"
                            )
                        }
                    }

                    OutlinedTextField(
                        value =
                            customFavoriteLabel,

                        onValueChange =
                            onCustomFavoriteLabelChanged,

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .CustomFavoriteField
                                ),

                        enabled =
                            !busy,

                        singleLine =
                            true,

                        keyboardOptions =
                            KeyboardOptions(
                                imeAction =
                                    ImeAction.Done
                            ),

                        keyboardActions =
                            KeyboardActions(
                                onDone = {
                                    if (
                                        !busy &&
                                        customFavoriteLabel
                                            .trim()
                                            .isNotEmpty()
                                    ) {
                                        focusManager
                                            .clearFocus()

                                        onSaveCustom()
                                    }
                                }
                            ),

                        label = {
                            Text(
                                "Freier Favoritenname"
                            )
                        },
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                6.dp
                            )
                    )

                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    NavigationUiTestTags
                                        .CustomFavoriteSave
                                ),

                        enabled =
                            !busy &&
                            customFavoriteLabel
                                .trim()
                                .isNotEmpty(),

                        onClick = {
                            focusManager
                                .clearFocus()

                            onSaveCustom()
                        },
                    ) {
                        Text(
                            "Als Favorit speichern"
                        )
                    }

                    TextButton(
                        modifier =
                            Modifier.fillMaxWidth(),

                        enabled =
                            !busy,

                        onClick =
                            onClearSelection,
                    ) {
                        Text(
                            "Markierung entfernen"
                        )
                    }
                }

                tripPlan
                    ?.let {
                            plan ->

                        Spacer(
                            modifier =
                                Modifier.height(
                                    12.dp
                                )
                        )

                        Text(
                            text =
                                "Reiseplan",

                            fontWeight =
                                FontWeight.SemiBold,
                        )

                        Text(
                            text =
                                "Ziel: " +
                                    (
                                        plan.destination
                                            .label
                                            ?: (
                                                plan.destination
                                                    .point
                                                    .latitude
                                                    .toString() +
                                                    ", " +
                                                    plan.destination
                                                        .point
                                                        .longitude
                                                        .toString()
                                            )
                                    ),

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,
                        )

                        if (
                            plan.viaPoints.isEmpty()
                        ) {
                            Text(
                                text =
                                    "Keine Zwischenziele.",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall,
                            )
                        } else {
                            plan.viaPoints
                                .forEachIndexed {
                                        index,
                                        stop ->

                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth(),

                                        verticalAlignment =
                                            Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text =
                                                "${index + 1}. " +
                                                    (
                                                        stop.label
                                                            ?: "Zwischenziel"
                                                    ),

                                            modifier =
                                                Modifier.weight(
                                                    1.0f
                                                ),

                                            style =
                                                MaterialTheme
                                                    .typography
                                                    .bodySmall,
                                        )

                                        TextButton(
                                            enabled =
                                                !busy &&
                                                index >
                                                    0,

                                            onClick = {
                                                onMoveViaUp(
                                                    index
                                                )
                                            },
                                        ) {
                                            Text(
                                                "↑"
                                            )
                                        }

                                        TextButton(
                                            enabled =
                                                !busy &&
                                                index <
                                                    plan.viaPoints
                                                        .lastIndex,

                                            onClick = {
                                                onMoveViaDown(
                                                    index
                                                )
                                            },
                                        ) {
                                            Text(
                                                "↓"
                                            )
                                        }

                                        TextButton(
                                            enabled =
                                                !busy,

                                            onClick = {
                                                onRemoveVia(
                                                    index
                                                )
                                            },
                                        ) {
                                            Text(
                                                "Löschen"
                                            )
                                        }
                                    }
                                }
                        }
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
                    Modifier
                        .fillMaxWidth()
                        .testTag(
                            NavigationUiTestTags
                                .PrimaryAction
                        ),

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
