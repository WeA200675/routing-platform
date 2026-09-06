package org.routingplatform.app.places

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import org.routingplatform.app.navigation.RoutePoint

object FavoriteDestinationCodec {

    fun encode(
        collection:
            FavoriteDestinationCollection,
    ): String {

        val buffer =
            ByteArrayOutputStream()

        DataOutputStream(
            buffer
        ).use {
                output ->

            output.writeInt(
                FAVORITE_DESTINATION_MAGIC
            )

            output.writeInt(
                FAVORITE_DESTINATION_VERSION
            )

            output.writeUTF(
                collection.profileId
            )

            output.writeOptionalFavorite(
                collection.home
            )

            output.writeOptionalFavorite(
                collection.work
            )

            output.writeInt(
                collection.custom.size
            )

            collection.custom
                .forEach {
                    output.writeFavorite(
                        it
                    )
                }
        }

        val bytes =
            buffer.toByteArray()

        require(
            bytes.size <=
                MAX_FAVORITE_DESTINATION_BYTES
        ) {
            "Favorite destination payload exceeds size limit."
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
    ): FavoriteDestinationCollection {

        require(
            encoded.isNotBlank()
        ) {
            "Favorite destination payload must not be blank."
        }

        require(
            encoded.length <=
                MAX_FAVORITE_DESTINATION_TEXT_LENGTH
        ) {
            "Favorite destination payload text exceeds size limit."
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
                    "Favorite destination payload is not valid Base64.",
                    error,
                )
            }

        require(
            bytes.size <=
                MAX_FAVORITE_DESTINATION_BYTES
        ) {
            "Favorite destination payload exceeds size limit."
        }

        return DataInputStream(
            ByteArrayInputStream(
                bytes
            )
        ).use {
                input ->

            require(
                input.readInt() ==
                    FAVORITE_DESTINATION_MAGIC
            ) {
                "Favorite destination payload magic mismatch."
            }

            require(
                input.readInt() ==
                    FAVORITE_DESTINATION_VERSION
            ) {
                "Unsupported favorite destination payload version."
            }

            val profileId =
                input.readUTF()

            val home =
                input.readOptionalFavorite(
                    FavoriteDestinationKind.Home
                )

            val work =
                input.readOptionalFavorite(
                    FavoriteDestinationKind.Work
                )

            val customCount =
                input.readInt()

            require(
                customCount in
                    0..MAX_CUSTOM_FAVORITE_DESTINATIONS
            ) {
                "Invalid custom favorite destination count."
            }

            val custom =
                buildList {
                    repeat(
                        customCount
                    ) {
                        add(
                            input.readFavorite(
                                FavoriteDestinationKind.Custom
                            )
                        )
                    }
                }

            require(
                input.available() ==
                    0
            ) {
                "Favorite destination payload contains trailing data."
            }

            FavoriteDestinationCollection(
                profileId =
                    profileId,

                home =
                    home,

                work =
                    work,

                custom =
                    custom,
            )
        }
    }
}

private fun DataOutputStream.writeOptionalFavorite(
    favorite:
        FavoriteDestination?,
) {
    writeBoolean(
        favorite !=
            null
    )

    favorite
        ?.let {
            writeFavorite(
                it
            )
        }
}

private fun DataOutputStream.writeFavorite(
    favorite:
        FavoriteDestination,
) {
    writeUTF(
        favorite.id
    )

    writeUTF(
        favorite.label
    )

    writeDouble(
        favorite.point.latitude
    )

    writeDouble(
        favorite.point.longitude
    )
}

private fun DataInputStream.readOptionalFavorite(
    kind:
        FavoriteDestinationKind,
): FavoriteDestination? =
    if (
        readBoolean()
    ) {
        readFavorite(
            kind
        )
    } else {
        null
    }

private fun DataInputStream.readFavorite(
    kind:
        FavoriteDestinationKind,
): FavoriteDestination =
    FavoriteDestination(
        id =
            readUTF(),

        kind =
            kind,

        label =
            readUTF(),

        point =
            RoutePoint(
                latitude =
                    readDouble(),

                longitude =
                    readDouble(),
            ),
    )

private const val FAVORITE_DESTINATION_MAGIC =
    0x52504656

private const val FAVORITE_DESTINATION_VERSION =
    1

private const val MAX_FAVORITE_DESTINATION_BYTES =
    128 * 1024

private const val MAX_FAVORITE_DESTINATION_TEXT_LENGTH =
    256 * 1024
