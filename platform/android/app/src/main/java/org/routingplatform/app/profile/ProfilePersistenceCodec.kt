package org.routingplatform.app.profile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/*
 * Small versioned persistence codec for explicit UserProfile settings.
 *
 * This codec stores only the authoritative profile contract.
 * Learned/AI/history payloads remain separate stores referenced only
 * by ProfileDataReferences.
 */
object ProfilePersistenceCodec {

    fun encode(
        profile:
            UserProfile,
    ): String {

        val buffer =
            ByteArrayOutputStream()

        DataOutputStream(
            buffer
        ).use {
                output ->

            output.writeInt(
                PROFILE_PERSISTENCE_MAGIC
            )

            output.writeInt(
                PROFILE_PERSISTENCE_VERSION
            )

            output.writeInt(
                profile.schemaVersion
            )

            output.writeUTF(
                profile.profileId
            )

            output.writeUTF(
                profile.displayName
            )

            output.writeBoolean(
                profile.voice.enabled
            )

            output.writeUTF(
                profile.voice.languageTag
            )

            output.writeOptionalString(
                profile.voice.voiceId
            )

            output.writeDouble(
                profile.voice.speechRate
            )

            output.writeUTF(
                profile.voice.verbosity.name
            )

            output.writeOptionalString(
                profile.voice
                    .preferredSpokenName
            )

            output.writeUTF(
                profile.driving.style.name
            )

            output.writeUTF(
                profile.driving.routeStyle.name
            )

            output.writeUTF(
                profile.driving.routeStability.name
            )

            output.writeBoolean(
                profile.driving.preferMajorRoads
            )

            output.writeBoolean(
                profile.driving.avoidComplexTurns
            )

            output.writeUTF(
                profile.navigation
                    .instructionLeadTime
                    .name
            )

            output.writeBoolean(
                profile.navigation
                    .repeatCriticalInstructions
            )

            output.writeBoolean(
                profile.navigation
                    .automaticMapZoom
            )

            output.writeBoolean(
                profile.navigation
                    .showLaneGuidance
            )

            output.writeBoolean(
                profile.navigation
                    .showRouteAlternatives
            )

            output.writeUTF(
                profile.display.appearance.name
            )

            output.writeUTF(
                profile.display.mapStyle.name
            )

            output.writeUTF(
                profile.display.mapOrientation.name
            )

            output.writeDouble(
                profile.display.mapTiltDegrees
            )

            output.writeDouble(
                profile.display.defaultZoom
            )

            output.writeUTF(
                profile.display
                    .informationDensity
                    .name
            )

            output.writeDouble(
                profile.display.textScale
            )

            output.writeDouble(
                profile.display.routeLineScale
            )

            output.writeUTF(
                profile.display
                    .navigationControlSide
                    .name
            )

            output.writeBoolean(
                profile.ai
                    .personalizationEnabled
            )

            output.writeBoolean(
                profile.ai.learningEnabled
            )

            output.writeBoolean(
                profile.ai
                    .useTripHistoryForPersonalization
            )

            output.writeUTF(
                profile.ai.assistantStyle.name
            )

            output.writeOptionalString(
                profile.dataReferences
                    .learnedPreferencesStoreId
            )

            output.writeOptionalString(
                profile.dataReferences
                    .tripHistoryStoreId
            )

            output.writeOptionalString(
                profile.dataReferences
                    .aiContextStoreId
            )

            output.writeUTF(
                profile.personality
                    .selectedPackId
            )

            output.writeUTF(
                profile.personality
                    .selectionSource
                    .name
            )

            output.writeBoolean(
                profile.personality
                    .weeklyDiscoveryEnabled
            )

            output.writeUTF(
                profile.personality
                    .weeklyDiscoveryIntensity
                    .name
            )
        }

        val bytes =
            buffer.toByteArray()

        require(
            bytes.size <=
                MAX_PROFILE_PERSISTENCE_BYTES
        ) {
            "Encoded profile exceeds persistence limit."
        }

        return Base64
            .getEncoder()
            .encodeToString(
                bytes
            )
    }

    fun decode(
        encoded:
            String,
    ): UserProfile {

        require(
            encoded.isNotBlank()
        ) {
            "Encoded profile must not be blank."
        }

        require(
            encoded.length <=
                MAX_PROFILE_PERSISTENCE_TEXT_LENGTH
        ) {
            "Encoded profile text exceeds persistence limit."
        }

        val bytes =
            try {
                Base64
                    .getDecoder()
                    .decode(
                        encoded
                    )
            } catch (
                error:
                    IllegalArgumentException
            ) {
                throw IllegalArgumentException(
                    "Encoded profile is not valid Base64.",
                    error,
                )
            }

        require(
            bytes.size <=
                MAX_PROFILE_PERSISTENCE_BYTES
        ) {
            "Decoded profile exceeds persistence limit."
        }

        return DataInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                input ->

            require(
                input.readInt() ==
                    PROFILE_PERSISTENCE_MAGIC
            ) {
                "Profile persistence magic mismatch."
            }

            val persistenceVersion =
                input.readInt()

            require(
                persistenceVersion in
                    MIN_SUPPORTED_PROFILE_PERSISTENCE_VERSION..
                        PROFILE_PERSISTENCE_VERSION
            ) {
                "Unsupported profile persistence version."
            }

            val persistedSchemaVersion =
                input.readInt()

            if (
                persistenceVersion ==
                    LEGACY_PROFILE_PERSISTENCE_VERSION
            ) {
                require(
                    persistedSchemaVersion ==
                        LEGACY_USER_PROFILE_SCHEMA_VERSION
                ) {
                    "Legacy profile schema version mismatch."
                }
            } else {
                require(
                    persistedSchemaVersion ==
                        USER_PROFILE_SCHEMA_VERSION
                ) {
                    "Profile schema version mismatch."
                }
            }

            val profile =
                UserProfile(
                    schemaVersion =
                        USER_PROFILE_SCHEMA_VERSION,

                    profileId =
                        input.readUTF(),

                    displayName =
                        input.readUTF(),

                    voice =
                        VoicePreferences(
                            enabled =
                                input.readBoolean(),

                            languageTag =
                                input.readUTF(),

                            voiceId =
                                input.readOptionalString(),

                            speechRate =
                                input.readDouble(),

                            verbosity =
                                input.readEnumValue(
                                    "voice verbosity"
                                ),

                            preferredSpokenName =
                                input.readOptionalString(),
                        ),

                    driving =
                        DrivingPreferences(
                            style =
                                input.readEnumValue(
                                    "driving style"
                                ),

                            routeStyle =
                                input.readEnumValue(
                                    "route style"
                                ),

                            routeStability =
                                input.readEnumValue(
                                    "route stability"
                                ),

                            preferMajorRoads =
                                input.readBoolean(),

                            avoidComplexTurns =
                                input.readBoolean(),
                        ),

                    navigation =
                        NavigationPreferences(
                            instructionLeadTime =
                                input.readEnumValue(
                                    "instruction lead time"
                                ),

                            repeatCriticalInstructions =
                                input.readBoolean(),

                            automaticMapZoom =
                                input.readBoolean(),

                            showLaneGuidance =
                                input.readBoolean(),

                            showRouteAlternatives =
                                input.readBoolean(),
                        ),

                    display =
                        DisplayPreferences(
                            appearance =
                                input.readEnumValue(
                                    "appearance"
                                ),

                            mapStyle =
                                input.readEnumValue(
                                    "map style"
                                ),

                            mapOrientation =
                                input.readEnumValue(
                                    "map orientation"
                                ),

                            mapTiltDegrees =
                                input.readDouble(),

                            defaultZoom =
                                input.readDouble(),

                            informationDensity =
                                input.readEnumValue(
                                    "information density"
                                ),

                            textScale =
                                input.readDouble(),

                            routeLineScale =
                                input.readDouble(),

                            navigationControlSide =
                                input.readEnumValue(
                                    "navigation control side"
                                ),
                        ),

                    ai =
                        AiPreferences(
                            personalizationEnabled =
                                input.readBoolean(),

                            learningEnabled =
                                input.readBoolean(),

                            useTripHistoryForPersonalization =
                                input.readBoolean(),

                            assistantStyle =
                                input.readEnumValue(
                                    "assistant style"
                                ),
                        ),

                    dataReferences =
                        ProfileDataReferences(
                            learnedPreferencesStoreId =
                                input.readOptionalString(),

                            tripHistoryStoreId =
                                input.readOptionalString(),

                            aiContextStoreId =
                                input.readOptionalString(),
                        ),

                    personality =
                        if (
                            persistenceVersion >=
                                2
                        ) {
                            NavigationPersonalityPreferences(
                                selectedPackId =
                                    input.readUTF(),

                                selectionSource =
                                    input.readEnumValue(
                                        "experience pack selection source"
                                    ),

                                weeklyDiscoveryEnabled =
                                    input.readBoolean(),

                                weeklyDiscoveryIntensity =
                                    input.readEnumValue(
                                        "weekly discovery intensity"
                                    ),
                            )
                        } else {
                            NavigationPersonalityPreferences()
                        },
                )

            require(
                input.available() ==
                    0
            ) {
                "Profile persistence payload contains trailing data."
            }

            profile
        }
    }
}

private fun DataOutputStream.writeOptionalString(
    value:
        String?,
) {
    writeBoolean(
        value !=
            null
    )

    value
        ?.let {
            writeUTF(
                it
            )
        }
}

private fun DataInputStream.readOptionalString():
    String? =
    if (
        readBoolean()
    ) {
        readUTF()
    } else {
        null
    }

private inline fun <
    reified T :
        Enum<T>
> DataInputStream.readEnumValue(
    fieldName:
        String,
): T {
    val value =
        readUTF()

    return enumValues<T>()
        .firstOrNull {
            it.name ==
                value
        }
        ?: throw IllegalArgumentException(
            "Unknown $fieldName value: $value"
        )
}

private const val PROFILE_PERSISTENCE_MAGIC =
    0x52504631

private const val LEGACY_PROFILE_PERSISTENCE_VERSION =
    1

private const val PROFILE_PERSISTENCE_VERSION =
    2

private const val MIN_SUPPORTED_PROFILE_PERSISTENCE_VERSION =
    LEGACY_PROFILE_PERSISTENCE_VERSION

private const val LEGACY_USER_PROFILE_SCHEMA_VERSION =
    1

private const val MAX_PROFILE_PERSISTENCE_BYTES =
    64 * 1024

private const val MAX_PROFILE_PERSISTENCE_TEXT_LENGTH =
    128 * 1024