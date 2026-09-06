package org.routingplatform.app.places

import org.routingplatform.app.navigation.RoutePoint

const val MAX_DESTINATION_SEARCH_RESULTS =
    8

data class DestinationSearchResult(
    val id:
        String,

    val primaryLabel:
        String,

    val secondaryLabel:
        String?,

    val point:
        RoutePoint,
) {
    init {
        require(
            id.isNotBlank() &&
                id.length <=
                    256
        ) {
            "Destination search result id is invalid."
        }

        requireSearchLabel(
            primaryLabel,
            "primaryLabel",
        )

        secondaryLabel
            ?.let {
                requireSearchLabel(
                    it,
                    "secondaryLabel",
                )
            }

        require(
            point.latitude.isFinite() &&
                point.latitude in
                    -90.0..90.0 &&
                point.longitude.isFinite() &&
                point.longitude in
                    -180.0..180.0
        ) {
            "Destination search result coordinates are invalid."
        }
    }

    val displayText:
        String
        get() =
            secondaryLabel
                ?.takeIf {
                    it.isNotBlank() &&
                        it !=
                        primaryLabel
                }
                ?.let {
                    "$primaryLabel — $it"
                }
                ?: primaryLabel
}

interface DestinationSearchHandle {
    fun cancel()
}

interface DestinationSearchSource :
    AutoCloseable {

    fun search(
        query:
            String,

        onResult:
            (
                Result<
                    List<DestinationSearchResult>
                >
            ) -> Unit,
    ): DestinationSearchHandle

    override fun close()
}

private fun requireSearchLabel(
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
            240
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
