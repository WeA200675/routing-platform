package org.routingplatform.app.profile

import android.content.Context

/*
 * Small local store for explicit user profile settings.
 *
 * It deliberately does NOT store trip history, learned preferences,
 * AI context, routing state or positioning/safety state.
 */
class AndroidUserProfileStore(
    context:
        Context,
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
    ): UserProfile {

        val activeProfileId =
            preferences.getString(
                ACTIVE_PROFILE_ID_KEY,
                null,
            )
                ?: return fallback

        val encoded =
            preferences.getString(
                profileKey(
                    activeProfileId
                ),
                null,
            )
                ?: return fallback

        return runCatching {
            ProfilePersistenceCodec
                .decode(
                    encoded
                )
        }.getOrElse {
            fallback
        }
    }

    @Synchronized
    fun saveAndActivate(
        profile:
            UserProfile,
    ): Boolean {

        val encoded =
            ProfilePersistenceCodec
                .encode(
                    profile
                )

        return preferences
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

private const val PROFILE_PREFERENCES_NAME =
    "routing-platform-user-profiles-v1"

private const val ACTIVE_PROFILE_ID_KEY =
    "active-profile-id"

private const val PROFILE_KEY_PREFIX =
    "profile:"