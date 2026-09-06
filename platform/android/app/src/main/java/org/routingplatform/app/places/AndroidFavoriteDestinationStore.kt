package org.routingplatform.app.places

import android.content.Context

/*
 * Explicit, profile-local saved places.
 *
 * This store is deliberately separate from UserProfile settings,
 * learned preferences, trip history and AI context.
 */
class AndroidFavoriteDestinationStore(
    context:
        Context,
) {
    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                FAVORITE_DESTINATION_PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )

    @Synchronized
    fun load(
        profileId:
            String,
    ): FavoriteDestinationCollection {

        val empty =
            FavoriteDestinationCollection
                .empty(
                    profileId
                )

        val encoded =
            preferences.getString(
                favoriteKey(
                    profileId
                ),
                null,
            )
                ?: return empty

        return runCatching {
            FavoriteDestinationCodec
                .decode(
                    encoded
                )
        }.mapCatching {
                decoded ->

            require(
                decoded.profileId ==
                    profileId
            ) {
                "Favorite destination profile mismatch."
            }

            decoded
        }.getOrElse {
            empty
        }
    }

    @Synchronized
    fun save(
        collection:
            FavoriteDestinationCollection,
    ): Boolean =
        preferences
            .edit()
            .putString(
                favoriteKey(
                    collection.profileId
                ),
                FavoriteDestinationCodec
                    .encode(
                        collection
                    ),
            )
            .commit()

    private fun favoriteKey(
        profileId:
            String,
    ): String {

        FavoriteDestinationCollection
            .empty(
                profileId
            )

        return FAVORITE_DESTINATION_KEY_PREFIX +
            profileId
    }
}

private const val FAVORITE_DESTINATION_PREFERENCES_NAME =
    "routing-platform-favorite-destinations-v1"

private const val FAVORITE_DESTINATION_KEY_PREFIX =
    "profile:"
