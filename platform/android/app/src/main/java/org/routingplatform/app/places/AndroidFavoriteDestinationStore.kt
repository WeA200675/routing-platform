package org.routingplatform.app.places

import android.content.Context
import android.util.Log
import org.routingplatform.app.navigation.NavigationPersistenceReliability
import org.routingplatform.app.navigation.NavigationReliabilityEvent

/*
 * Explicit, profile-local saved places.
 *
 * This store is deliberately separate from UserProfile settings,
 * learned preferences, trip history and AI context.
 */
class AndroidFavoriteDestinationStore(
    context:
        Context,

    private val onReliabilityEvent:
        (NavigationReliabilityEvent) -> Unit =
        ::logFavoritePersistenceEvent,
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

        return NavigationPersistenceReliability
            .loadOrFallback(
                fallback =
                    empty,

                operationName =
                    "favorite-destinations.load",

                onReliabilityEvent =
                    onReliabilityEvent,
            ) {
                val encoded =
                    preferences.getString(
                        favoriteKey(
                            profileId
                        ),
                        null,
                    )
                        ?: return@loadOrFallback empty

                val decoded =
                    FavoriteDestinationCodec
                        .decode(
                            encoded
                        )

                require(
                    decoded.profileId ==
                        profileId
                ) {
                    "Favorite destination profile mismatch."
                }

                decoded
            }
    }

    @Synchronized
    fun save(
        collection:
            FavoriteDestinationCollection,
    ): Boolean =
        NavigationPersistenceReliability
            .saveWithSingleRetry(
                operationName =
                    "favorite-destinations.save",

                onReliabilityEvent =
                    onReliabilityEvent,
            ) {
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
            }

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

private fun logFavoritePersistenceEvent(
    event:
        NavigationReliabilityEvent,
) {
    Log.w(
        FAVORITE_STORE_LOG_TAG,
        "${event.kind}/${event.fault.code}/${event.fault.domain}",
    )
}

private const val FAVORITE_STORE_LOG_TAG =
    "RoutingFavoriteStore"

private const val FAVORITE_DESTINATION_PREFERENCES_NAME =
    "routing-platform-favorite-destinations-v1"

private const val FAVORITE_DESTINATION_KEY_PREFIX =
    "profile:"
