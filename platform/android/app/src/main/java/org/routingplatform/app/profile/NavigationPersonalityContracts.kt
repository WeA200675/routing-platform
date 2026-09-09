package org.routingplatform.app.profile

/**
 * Presentation-only personalization contract.
 *
 * Experience packs may change tone, verbosity, map presentation and other
 * non-authoritative UX choices. They MUST NOT change maneuver truth, route
 * geometry, positioning, progress, permission enforcement or safety gates.
 */
enum class NavigationPersonalityTone {
    Neutral,
    Calm,
    Warm,
    DryHumor,
    Futuristic,
    CoPilot,
}

enum class ExperiencePackGenre {
    Classic,
    Calm,
    ScienceFiction,
    CoPilot,
    Seasonal,
    Experimental,
}

enum class WeeklyDiscoveryIntensity(
    val maximumSurpriseLevel:
        Int,
) {
    Subtle(
        maximumSurpriseLevel =
            1,
    ),

    Creative(
        maximumSurpriseLevel =
            2,
    ),

    Wild(
        maximumSurpriseLevel =
            3,
    ),
}

enum class ExperiencePackSelectionSource {
    Default,
    Explicit,
    WeeklyDiscovery,
}

enum class ExperiencePackFeedback {
    Like,
    NotForMe,
    MoreLikeThis,
    KeepPermanently,
}

data class ExperiencePackDefinition(
    val packId:
        String,

    val version:
        Int,

    val displayName:
        String,

    val description:
        String,

    val genre:
        ExperiencePackGenre,

    val tone:
        NavigationPersonalityTone,

    val voiceVerbosity:
        VoiceGuidanceVerbosity,

    val mapStyle:
        ProfileMapStyle,

    val humorLevel:
        Int,

    val warmthLevel:
        Int,

    val directnessLevel:
        Int,

    val surpriseLevel:
        Int,
) {
    init {
        require(
            EXPERIENCE_PACK_ID_PATTERN.matches(
                packId
            )
        ) {
            "packId must contain 1-64 lowercase safe identifier characters."
        }

        require(
            version >=
                1
        ) {
            "version must be at least 1."
        }

        requireValidExperiencePackLabel(
            value =
                displayName,

            fieldName =
                "displayName",

            maximumLength =
                64,
        )

        requireValidExperiencePackLabel(
            value =
                description,

            fieldName =
                "description",

            maximumLength =
                240,
        )

        require(
            humorLevel in
                0..100
        ) {
            "humorLevel must be in [0, 100]."
        }

        require(
            warmthLevel in
                0..100
        ) {
            "warmthLevel must be in [0, 100]."
        }

        require(
            directnessLevel in
                0..100
        ) {
            "directnessLevel must be in [0, 100]."
        }

        require(
            surpriseLevel in
                0..3
        ) {
            "surpriseLevel must be in [0, 3]."
        }
    }
}

data class NavigationPersonalityPreferences(
    val selectedPackId:
        String =
        ExperiencePackCatalog.CLASSIC_PACK_ID,

    val selectionSource:
        ExperiencePackSelectionSource =
        ExperiencePackSelectionSource.Default,

    val weeklyDiscoveryEnabled:
        Boolean =
        false,

    val weeklyDiscoveryIntensity:
        WeeklyDiscoveryIntensity =
        WeeklyDiscoveryIntensity.Creative,
) {
    init {
        require(
            EXPERIENCE_PACK_ID_PATTERN.matches(
                selectedPackId
            )
        ) {
            "selectedPackId must contain 1-64 lowercase safe identifier characters."
        }
    }

    val hasExplicitPackSelection:
        Boolean
        get() =
            selectionSource ==
                ExperiencePackSelectionSource.Explicit
}

/**
 * Built-in packs are intentionally original generic product themes.
 * No third-party character, franchise, celebrity voice or protected sound is
 * required by this catalog.
 */
object ExperiencePackCatalog {

    const val CLASSIC_PACK_ID =
        "classic"

    const val ZEN_PACK_ID =
        "zen"

    const val GALACTIC_PACK_ID =
        "galactic"

    val builtIns:
        List<ExperiencePackDefinition> =
        listOf(
            ExperiencePackDefinition(
                packId =
                    CLASSIC_PACK_ID,

                version =
                    1,

                displayName =
                    "Classic",

                description =
                    "Sachlich, klar und vertraut.",

                genre =
                    ExperiencePackGenre.Classic,

                tone =
                    NavigationPersonalityTone.Neutral,

                voiceVerbosity =
                    VoiceGuidanceVerbosity.Standard,

                mapStyle =
                    ProfileMapStyle.Standard,

                humorLevel =
                    0,

                warmthLevel =
                    35,

                directnessLevel =
                    90,

                surpriseLevel =
                    0,
            ),

            ExperiencePackDefinition(
                packId =
                    ZEN_PACK_ID,

                version =
                    1,

                displayName =
                    "Zen",

                description =
                    "Ruhig, reduziert und angenehm unaufdringlich.",

                genre =
                    ExperiencePackGenre.Calm,

                tone =
                    NavigationPersonalityTone.Calm,

                voiceVerbosity =
                    VoiceGuidanceVerbosity.Minimal,

                mapStyle =
                    ProfileMapStyle.Minimal,

                humorLevel =
                    5,

                warmthLevel =
                    70,

                directnessLevel =
                    70,

                surpriseLevel =
                    1,
            ),

            ExperiencePackDefinition(
                packId =
                    GALACTIC_PACK_ID,

                version =
                    1,

                displayName =
                    "Galactic",

                description =
                    "Futuristisch, trocken-humorvoll und eigenstaendig.",

                genre =
                    ExperiencePackGenre.ScienceFiction,

                tone =
                    NavigationPersonalityTone.Futuristic,

                voiceVerbosity =
                    VoiceGuidanceVerbosity.Standard,

                mapStyle =
                    ProfileMapStyle.Night,

                humorLevel =
                    55,

                warmthLevel =
                    40,

                directnessLevel =
                    80,

                surpriseLevel =
                    2,
            ),
        )

    init {
        require(
            builtIns
                .map {
                    it.packId
                }
                .distinct()
                .size ==
                builtIns.size
        ) {
            "Built-in experience pack ids must be unique."
        }
    }

    fun find(
        packId:
            String,
    ): ExperiencePackDefinition? =
        builtIns
            .firstOrNull {
                it.packId ==
                    packId
            }

    fun require(
        packId:
            String,
    ): ExperiencePackDefinition =
        find(
            packId
        )
            ?: throw IllegalArgumentException(
                "Unknown experience pack: $packId"
            )
}

/**
 * Explicit user choice is authoritative.
 *
 * A weekly suggestion can only become effective when the user has not pinned
 * an explicit pack. Learned or server-side suggestion systems must call this
 * boundary instead of overwriting explicit profile settings.
 */
object ExperiencePackSelectionPolicy {

    fun resolve(
        preferences:
            NavigationPersonalityPreferences,

        weeklySuggestionPackId:
            String? =
            null,
    ): ExperiencePackDefinition {

        if (
            preferences
                .hasExplicitPackSelection
        ) {
            return ExperiencePackCatalog
                .require(
                    preferences
                        .selectedPackId
                )
        }

        if (
            preferences
                .weeklyDiscoveryEnabled
        ) {
            weeklySuggestionPackId
                ?.let {
                    ExperiencePackCatalog
                        .find(
                            it
                        )
                }
                ?.let {
                    return it
                }
        }

        return ExperiencePackCatalog
            .require(
                preferences
                    .selectedPackId
            )
    }
}

/**
 * Deterministic local fallback for "Woechentlich ueberraschen".
 *
 * Cloud/AI discovery may later supply additional candidates. This fallback
 * guarantees that the feature remains useful offline and testable without
 * allowing it to mutate route or positioning truth.
 */
object WeeklyExperiencePackSelector {

    fun select(
        weekKey:
            String,

        currentPackId:
            String,

        intensity:
            WeeklyDiscoveryIntensity,

        rejectedPackIds:
            Set<String> =
            emptySet(),
    ): ExperiencePackDefinition {

        require(
            weekKey.isNotBlank() &&
                weekKey.length <=
                64
        ) {
            "weekKey must be non-blank and at most 64 characters."
        }

        val candidates =
            ExperiencePackCatalog
                .builtIns
                .filter {
                    it.packId !=
                        currentPackId &&
                        it.packId !in
                            rejectedPackIds &&
                        it.surpriseLevel <=
                            intensity
                                .maximumSurpriseLevel
                }

        if (
            candidates.isEmpty()
        ) {
            return ExperiencePackCatalog
                .require(
                    currentPackId
                )
        }

        val stableIndex =
            Math.floorMod(
                (
                    "$weekKey|" +
                        intensity.name
                ).hashCode(),
                candidates.size,
            )

        return candidates[
            stableIndex
        ]
    }
}

private val EXPERIENCE_PACK_ID_PATTERN =
    Regex(
        "[a-z0-9][a-z0-9._-]{0,63}"
    )

private fun requireValidExperiencePackLabel(
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