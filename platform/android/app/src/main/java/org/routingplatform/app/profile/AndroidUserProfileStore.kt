package org.routingplatform.app.profile

import android.content.Context
import android.util.Log
import org.routingplatform.app.navigation.NavigationPersistenceReliability
import org.routingplatform.app.navigation.NavigationReliabilityEvent

/*
 * Small local store for explicit user profile settings.
 *
 * It deliberately does NOT store trip history, learned preferences,
 * AI context, routing state or positioning/safety state.
 */
class AndroidUserProfileStore(
    context:
        Context,

    private val onReliabilityEvent:
        (NavigationReliabilityEvent) -> Unit =
        ::logUserProfilePersistenceEvent,
) {
    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PROFILE_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )

    @Synchronized
    fun loadActiveProfile(
        fallback:
            UserProfile =
            ProfileDefaults.guest(),
    ): UserProfile =
        NavigationPersistenceReliability
            .loadOrFallback(
                fallback =
                    fallback,

                operationName =
                    "user-profile.load",

                onReliabilityEvent =
                    onReliabilityEvent,
            ) {
                val activeProfileId =
                    preferences.getString(
                        ACTIVE_PROFILE_ID_KEY,
                        null,
                    )
                        ?: return@loadOrFallback fallback

                val encoded =
                    preferences.getString(
                        profileKey(
                            activeProfileId
                        ),
                        null,
                    )
                        ?: return@loadOrFallback fallback

                val decoded =
                    ProfilePersistenceCodec
                        .decode(
                            encoded
                        )

                require(
                    decoded.profileId ==
                        activeProfileId
                ) {
                    "Active profile id does not match stored profile."
                }

                decoded
            }

    @Synchronized
    fun saveAndActivate(
        profile:
            UserProfile,
    ): Boolean =
        NavigationPersistenceReliability
            .saveWithSingleRetry(
                operationName =
                    "user-profile.save",

                onReliabilityEvent =
                    onReliabilityEvent,
            ) {
                val encoded =
                    ProfilePersistenceCodec
                        .encode(
                            profile
                        )

                preferences
                    .edit()
                    .putString(
                        profileKey(
                            profile.profileId
                        ),
                        encoded,
                    )
                    .putString(
                        ACTIVE_PROFILE_ID_KEY,
                        profile.profileId,
                    )
                    .commit()
            }

    private fun profileKey(
        profileId:
            String,
    ): String =
        PROFILE_KEY_PREFIX +
            profileId
}

private fun logUserProfilePersistenceEvent(
    event:
        NavigationReliabilityEvent,
) {
    Log.w(
        USER_PROFILE_STORE_LOG_TAG,
        "${event.kind}/${event.fault.code}/${event.fault.domain}",
    )
}

private const val USER_PROFILE_STORE_LOG_TAG =
    "RoutingProfileStore"

private const val PROFILE_PREFERENCES_NAME =
    "routing-platform-user-profiles-v1"

private const val ACTIVE_PROFILE_ID_KEY =
    "active-profile-id"

private const val PROFILE_KEY_PREFIX =
    "profile:"