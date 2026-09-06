package org.routingplatform.app.places

import org.routingplatform.app.navigation.RoutePoint

const val MAX_CUSTOM_FAVORITE_DESTINATIONS =
    50

enum class FavoriteDestinationKind {
    Home,
    Work,
    Custom,
}

data class FavoriteDestination(
    val id:
        String,

    val kind:
        FavoriteDestinationKind,

    val label:
        String,

    val point:
        RoutePoint,
) {
    init {
        require(
            FAVORITE_ID_PATTERN.matches(
                id
            )
        ) {
            "Favorite destination id must contain 1-80 safe identifier characters."
        }

        requireHumanLabel(
            value =
                label,

            fieldName =
                "Favorite destination label",
        )

        requireValidPoint(
            point
        )
    }
}

data class FavoriteDestinationCollection(
    val profileId:
        String,

    val home:
        FavoriteDestination? =
        null,

    val work:
        FavoriteDestination? =
        null,

    val custom:
        List<FavoriteDestination> =
        emptyList(),
) {
    init {
        require(
            PROFILE_ID_PATTERN.matches(
                profileId
            )
        ) {
            "Favorite destination profile id is invalid."
        }

        home
            ?.let {
                require(
                    it.kind ==
                        FavoriteDestinationKind.Home
                ) {
                    "Home slot must contain a Home favorite."
                }

                require(
                    it.id ==
                        HOME_FAVORITE_ID
                ) {
                    "Home favorite must use the reserved home id."
                }
            }

        work
            ?.let {
                require(
                    it.kind ==
                        FavoriteDestinationKind.Work
                ) {
                    "Work slot must contain a Work favorite."
                }

                require(
                    it.id ==
                        WORK_FAVORITE_ID
                ) {
                    "Work favorite must use the reserved work id."
                }
            }

        require(
            custom.size <=
                MAX_CUSTOM_FAVORITE_DESTINATIONS
        ) {
            "Too many custom favorite destinations."
        }

        custom.forEach {
            require(
                it.kind ==
                    FavoriteDestinationKind.Custom
            ) {
                "Custom favorite list contains another favorite kind."
            }

            require(
                it.id !=
                    HOME_FAVORITE_ID &&
                    it.id !=
                    WORK_FAVORITE_ID
            ) {
                "Custom favorite uses a reserved id."
            }
        }

        val allIds =
            all()
                .map {
                    it.id
                }

        require(
            allIds.distinct().size ==
                allIds.size
        ) {
            "Favorite destination ids must be unique."
        }
    }

    fun all():
        List<FavoriteDestination> =
        buildList {
            home
                ?.let {
                    add(
                        it
                    )
                }

            work
                ?.let {
                    add(
                        it
                    )
                }

            addAll(
                custom
            )
        }

    fun withHome(
        point:
            RoutePoint,
    ): FavoriteDestinationCollection =
        copy(
            home =
                FavoriteDestination(
                    id =
                        HOME_FAVORITE_ID,

                    kind =
                        FavoriteDestinationKind.Home,

                    label =
                        "Zuhause",

                    point =
                        point,
                )
        )

    fun withWork(
        point:
            RoutePoint,
    ): FavoriteDestinationCollection =
        copy(
            work =
                FavoriteDestination(
                    id =
                        WORK_FAVORITE_ID,

                    kind =
                        FavoriteDestinationKind.Work,

                    label =
                        "Arbeit",

                    point =
                        point,
                )
        )

    fun addCustom(
        id:
            String,

        label:
            String,

        point:
            RoutePoint,
    ): FavoriteDestinationCollection {

        require(
            custom.none {
                it.id ==
                    id
            }
        ) {
            "Custom favorite id already exists."
        }

        require(
            custom.size <
                MAX_CUSTOM_FAVORITE_DESTINATIONS
        ) {
            "Too many custom favorite destinations."
        }

        return copy(
            custom =
                custom +
                    FavoriteDestination(
                        id =
                            id,

                        kind =
                            FavoriteDestinationKind.Custom,

                        label =
                            label.trim(),

                        point =
                            point,
                    )
        )
    }

    fun remove(
        id:
            String,
    ): FavoriteDestinationCollection =
        when (
            id
        ) {
            HOME_FAVORITE_ID ->
                copy(
                    home =
                        null
                )

            WORK_FAVORITE_ID ->
                copy(
                    work =
                        null
                )

            else ->
                copy(
                    custom =
                        custom.filterNot {
                            it.id ==
                                id
                        }
                )
        }

    companion object {
        fun empty(
            profileId:
                String,
        ): FavoriteDestinationCollection =
            FavoriteDestinationCollection(
                profileId =
                    profileId
            )
    }
}

private fun requireValidPoint(
    point:
        RoutePoint,
) {
    require(
        point.latitude.isFinite() &&
            point.latitude in
                -90.0..90.0
    ) {
        "Favorite destination latitude is invalid."
    }

    require(
        point.longitude.isFinite() &&
            point.longitude in
                -180.0..180.0
    ) {
        "Favorite destination longitude is invalid."
    }
}

private fun requireHumanLabel(
    value:
        String,

    fieldName:
        String,
) {
    val trimmed =
        value.trim()

    require(
        trimmed.isNotEmpty()
    ) {
        "$fieldName must not be blank."
    }

    require(
        trimmed.length <=
            120
    ) {
        "$fieldName is too long."
    }

    require(
        trimmed.none {
            it.code <
                0x20 ||
                it.code ==
                0x7f
        }
    ) {
        "$fieldName contains control characters."
    }
}

private val PROFILE_ID_PATTERN =
    Regex(
        "[A-Za-z0-9._-]{1,64}"
    )

private val FAVORITE_ID_PATTERN =
    Regex(
        "[A-Za-z0-9._-]{1,80}"
    )

private const val HOME_FAVORITE_ID =
    "home"

private const val WORK_FAVORITE_ID =
    "work"
