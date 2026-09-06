package org.routingplatform.app.profile

/*
 * User personalization boundary.
 *
 * These values may influence presentation, voice, route preferences
 * and optional AI personalization.
 *
 * They MUST NOT alter positioning integrity, confidence thresholds,
 * route-progress safety gates, permission enforcement or other
 * safety-critical runtime boundaries.
 */
const val USER_PROFILE_SCHEMA_VERSION =
    1

enum class VoiceGuidanceVerbosity {
    Minimal,
    Standard,
    Detailed,
}

enum class DrivingStylePreference {
    Relaxed,
    Balanced,
    Direct,
}

enum class RouteStylePreference {
    Balanced,
    Stable,
    MajorRoads,
    SimpleManeuvers,
}

enum class RouteStabilityPreference {
    Responsive,
    Balanced,
    Stable,
}

enum class InstructionLeadTimePreference {
    Late,
    Standard,
    Early,
}

enum class ProfileAppearance {
    System,
    Light,
    Dark,
}

enum class ProfileMapStyle {
    Standard,
    Minimal,
    HighContrast,
    Night,
}

enum class ProfileMapOrientation {
    HeadingUp,
    NorthUp,
}

enum class InformationDensityPreference {
    Minimal,
    Standard,
    Detailed,
}

enum class AssistantStylePreference {
    Concise,
    Standard,
    Detailed,
}

data class VoicePreferences(
    val enabled:
        Boolean =
        true,

    val languageTag:
        String =
        "de-DE",

    val voiceId:
        String? =
        null,

    val speechRate:
        Double =
        1.0,

    val verbosity:
        VoiceGuidanceVerbosity =
        VoiceGuidanceVerbosity.Standard,

    val preferredSpokenName:
        String? =
        null,
) {
    init {
        require(
            languageTag.isNotBlank()
        ) {
            "languageTag must not be blank."
        }

        require(
            languageTag.length <= 35
        ) {
            "languageTag is too long."
        }

        require(
            languageTag.all {
                it.isLetterOrDigit() ||
                    it == '-'
            }
        ) {
            "languageTag contains unsupported characters."
        }

        require(
            speechRate.isFinite() &&
                speechRate in
                    0.5..2.0
        ) {
            "speechRate must be finite and in [0.5, 2.0]."
        }

        voiceId
            ?.let {
                require(
                    it.isNotBlank() &&
                        it.length <= 128
                ) {
                    "voiceId must be non-blank and at most 128 characters."
                }
            }

        preferredSpokenName
            ?.let {
                requireValidHumanLabel(
                    value =
                        it,

                    fieldName =
                        "preferredSpokenName",

                    maximumLength =
                        80,
                )
            }
    }
}

data class DrivingPreferences(
    val style:
        DrivingStylePreference =
        DrivingStylePreference.Balanced,

    val routeStyle:
        RouteStylePreference =
        RouteStylePreference.Balanced,

    val routeStability:
        RouteStabilityPreference =
        RouteStabilityPreference.Balanced,

    val preferMajorRoads:
        Boolean =
        false,

    val avoidComplexTurns:
        Boolean =
        false,
)

data class NavigationPreferences(
    val instructionLeadTime:
        InstructionLeadTimePreference =
        InstructionLeadTimePreference.Standard,

    val repeatCriticalInstructions:
        Boolean =
        true,

    val automaticMapZoom:
        Boolean =
        true,

    val showLaneGuidance:
        Boolean =
        true,

    val showRouteAlternatives:
        Boolean =
        true,
)

data class DisplayPreferences(
    val appearance:
        ProfileAppearance =
        ProfileAppearance.System,

    val mapStyle:
        ProfileMapStyle =
        ProfileMapStyle.Standard,

    val mapOrientation:
        ProfileMapOrientation =
        ProfileMapOrientation.HeadingUp,

    val mapTiltDegrees:
        Double =
        45.0,

    val defaultZoom:
        Double =
        16.0,

    val informationDensity:
        InformationDensityPreference =
        InformationDensityPreference.Standard,

    val textScale:
        Double =
        1.0,

    val routeLineScale:
        Double =
        1.0,
) {
    init {
        require(
            mapTiltDegrees.isFinite() &&
                mapTiltDegrees in
                    0.0..60.0
        ) {
            "mapTiltDegrees must be finite and in [0, 60]."
        }

        require(
            defaultZoom.isFinite() &&
                defaultZoom in
                    4.0..22.0
        ) {
            "defaultZoom must be finite and in [4, 22]."
        }

        require(
            textScale.isFinite() &&
                textScale in
                    0.8..1.5
        ) {
            "textScale must be finite and in [0.8, 1.5]."
        }

        require(
            routeLineScale.isFinite() &&
                routeLineScale in
                    0.75..2.0
        ) {
            "routeLineScale must be finite and in [0.75, 2.0]."
        }
    }
}

data class AiPreferences(
    /*
     * Explicit user settings may personalize the assistant
     * immediately.
     *
     * Learning remains opt-in and is intentionally separate.
     */
    val personalizationEnabled:
        Boolean =
        true,

    val learningEnabled:
        Boolean =
        false,

    val useTripHistoryForPersonalization:
        Boolean =
        false,

    val assistantStyle:
        AssistantStylePreference =
        AssistantStylePreference.Standard,
)

data class ProfileDataReferences(
    /*
     * References only.
     *
     * Large history / AI data is deliberately NOT embedded into
     * UserProfile. Stores can later be loaded lazily after a profile
     * has become active.
     */
    val learnedPreferencesStoreId:
        String? =
        null,

    val tripHistoryStoreId:
        String? =
        null,

    val aiContextStoreId:
        String? =
        null,
) {
    init {
        validateOptionalStoreId(
            value =
                learnedPreferencesStoreId,

            fieldName =
                "learnedPreferencesStoreId",
        )

        validateOptionalStoreId(
            value =
                tripHistoryStoreId,

            fieldName =
                "tripHistoryStoreId",
        )

        validateOptionalStoreId(
            value =
                aiContextStoreId,

            fieldName =
                "aiContextStoreId",
        )
    }
}

data class UserProfile(
    val schemaVersion:
        Int =
        USER_PROFILE_SCHEMA_VERSION,

    val profileId:
        String,

    val displayName:
        String,

    val voice:
        VoicePreferences =
        VoicePreferences(),

    val driving:
        DrivingPreferences =
        DrivingPreferences(),

    val navigation:
        NavigationPreferences =
        NavigationPreferences(),

    val display:
        DisplayPreferences =
        DisplayPreferences(),

    val ai:
        AiPreferences =
        AiPreferences(),

    val dataReferences:
        ProfileDataReferences =
        ProfileDataReferences(),
) {
    init {
        require(
            schemaVersion ==
                USER_PROFILE_SCHEMA_VERSION
        ) {
            "Only UserProfile schema v1 is supported."
        }

        require(
            PROFILE_ID_PATTERN.matches(
                profileId
            )
        ) {
            "profileId must contain 1-64 safe identifier characters."
        }

        requireValidHumanLabel(
            value =
                displayName,

            fieldName =
                "displayName",

            maximumLength =
                80,
        )
    }
}

/*
 * Immutable active-profile boundary.
 *
 * This will later become the single profile context supplied to
 * UI, voice and route-preference adapters.
 */
data class ActiveProfileSession(
    val profile:
        UserProfile,
) {
    val profileId:
        String
        get() =
            profile.profileId

    val displayName:
        String
        get() =
            profile.displayName

    val explicitPersonalizationEnabled:
        Boolean
        get() =
            profile.ai
                .personalizationEnabled

    val learningEnabled:
        Boolean
        get() =
            profile.ai
                .learningEnabled
}

private val PROFILE_ID_PATTERN =
    Regex(
        "[A-Za-z0-9._-]{1,64}"
    )

private fun requireValidHumanLabel(
    value:
        String,

    fieldName:
        String,

    maximumLength:
        Int,
) {
    require(
        value.isNotBlank()
    ) {
        "$fieldName must not be blank."
    }

    require(
        value.length <=
            maximumLength
    ) {
        "$fieldName is too long."
    }

    require(
        value.none {
            it.code <
                0x20 ||
                it.code ==
                0x7f
        }
    ) {
        "$fieldName must not contain control characters."
    }
}

private fun validateOptionalStoreId(
    value:
        String?,

    fieldName:
        String,
) {
    value
        ?.let {
            require(
                PROFILE_ID_PATTERN.matches(
                    it
                )
            ) {
                "$fieldName must contain 1-64 safe identifier characters."
            }
        }
}