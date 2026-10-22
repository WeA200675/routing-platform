package org.routingplatform.app.places

import org.routingplatform.app.navigation.RoutePoint

enum class RestroomFilter { Any, WithRestroom, WithoutRestroom }
enum class RestroomStatus { Present, Absent, Unknown }

data class RoadsideStop(
    val id: String,
    val name: String,
    val point: RoutePoint,
    val restroomStatus: RestroomStatus,
    val wheelchairAccessible: Boolean?,
) {
    fun toDestinationResult() = DestinationSearchResult(
        id = id,
        primaryLabel = name,
        secondaryLabel = buildString {
            append("WC: ")
            append(
                when (restroomStatus) {
                    RestroomStatus.Present -> "vorhanden"
                    RestroomStatus.Absent -> "nicht vorhanden"
                    RestroomStatus.Unknown -> "unbekannt"
                },
            )
            wheelchairAccessible?.let {
                append(if (it) " · barrierefrei" else " · nicht barrierefrei")
            }
            append(" · Zufahrt vor Ort prüfen")
            append(" · OpenStreetMap")
        },
        point = point,
    )
}

internal object RoadsideStopTagParser {
    fun isRestArea(tags: Map<String, String>): Boolean =
        tags["highway"] in setOf("services", "rest_area") ||
            tags["amenity"] == "rest_area"

    fun restroomStatus(tags: Map<String, String>): RestroomStatus =
        when (tags["toilets"]?.trim()?.lowercase()) {
            "yes", "designated" -> RestroomStatus.Present
            "no" -> RestroomStatus.Absent
            else -> RestroomStatus.Unknown
        }

    fun wheelchairAccessible(tags: Map<String, String>): Boolean? =
        when (tags["toilets:wheelchair"]?.trim()?.lowercase()) {
            "yes", "designated" -> true
            "no" -> false
            else -> null
        }

    fun matches(status: RestroomStatus, filter: RestroomFilter): Boolean =
        when (filter) {
            RestroomFilter.Any -> true
            RestroomFilter.WithRestroom -> status == RestroomStatus.Present
            RestroomFilter.WithoutRestroom -> status == RestroomStatus.Absent
        }
}

internal data class RoadsideStopSearchRequest(
    val center: RoutePoint,
    val restroomFilter: RestroomFilter,
) {
    init {
        require(center.latitude.isFinite() && center.latitude in -90.0..90.0)
        require(center.longitude.isFinite() && center.longitude in -180.0..180.0)
    }
}

internal fun parseRoadsideStopQuery(query: String): RestroomFilter? {
    val normalized = query.trim().lowercase()
    val isRestStopQuery =
        normalized.startsWith("raststätte") ||
            normalized.startsWith("raststaette") ||
            normalized.startsWith("rastplatz") ||
            normalized.startsWith("service area")
    val isRestroomQuery =
        normalized == "wc" ||
            normalized.startsWith("wc ") ||
            normalized.contains(" wc") ||
            normalized.startsWith("toilette") ||
            normalized.startsWith("toiletten")
    if (!isRestStopQuery && !isRestroomQuery) return null
    return when {
        normalized.contains("ohne wc") || normalized.contains("ohne toilette") ->
            RestroomFilter.WithoutRestroom
        normalized.contains("mit wc") || normalized.contains("mit toilette") ->
            RestroomFilter.WithRestroom
        else -> RestroomFilter.Any
    }
}
