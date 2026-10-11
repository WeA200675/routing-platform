package org.routingplatform.app.navigation

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

enum class NavigationRouteFamily {
    Fastest,
    Shortest,
    ProfileOptimal,
    MajorRoads,
    Comfort,
    LowUrban,
    LowCurvature,
    LowGradient,
    LowTraffic,
    Energy,
    Scenic,
    Stable,
}

enum class NavigationRouteSegmentDataStatus {
    Unspecified,
    Complete,
    Unavailable,
}

data class NavigationRouteDiagnostic(
    val code: String,
    val message: String,
) {
    init {
        require(code.isNotBlank()) {
            "Navigation route diagnostic code must not be blank."
        }
    }
}

data class NavigationRouteContractManeuver(
    val type: ManeuverType,
    val instruction: String,
    val streetNames: List<String>,
    val distanceM: Double,
    val durationS: Double,
    val beginShapeIndex: Int,
    val endShapeIndex: Int,
    val bearingBeforeDeg: Int?,
    val bearingAfterDeg: Int?,
    val engineType: Int?,
) {
    init {
        require(
            distanceM.isFinite() &&
                distanceM >= 0.0
        )

        require(
            durationS.isFinite() &&
                durationS >= 0.0
        )

        require(beginShapeIndex >= 0)
        require(endShapeIndex >= beginShapeIndex)

        streetNames.forEach {
            require(it.isNotBlank()) {
                "Navigation street name must not be blank."
            }
        }

        bearingBeforeDeg?.let {
            require(it in 0..359)
        }

        bearingAfterDeg?.let {
            require(it in 0..359)
        }
    }

    fun toNavigationManeuver():
        NavigationManeuver =
        NavigationManeuver(
            type =
                type,

            instruction =
                instruction,

            distanceM =
                distanceM,

            durationS =
                durationS,

            streetNames =
                streetNames,

            beginShapeIndex =
                beginShapeIndex,

            endShapeIndex =
                endShapeIndex,

            bearingBeforeDeg =
                bearingBeforeDeg,

            bearingAfterDeg =
                bearingAfterDeg,

            engineType =
                engineType,
        )
}

data class NavigationRouteContract(
    val schemaVersion: Int = 1,

    val routeId: String,
    val family: NavigationRouteFamily,

    val distanceM: Double,
    val durationS: Double,

    val geometry: List<RoutePoint>,

    val maneuvers:
        List<NavigationRouteContractManeuver>,

    val engineName: String,
    val engineVersion: String,

    val segmentDataStatus:
        NavigationRouteSegmentDataStatus,

    val diagnostics:
        List<NavigationRouteDiagnostic>,
) {
    init {
        require(schemaVersion == 1) {
            "Unsupported navigation route contract schema."
        }

        require(routeId.isNotBlank()) {
            "Navigation route id must not be blank."
        }

        require(
            distanceM.isFinite() &&
                distanceM > 0.0
        )

        require(
            durationS.isFinite() &&
                durationS > 0.0
        )

        require(geometry.size >= 2) {
            "Navigation route requires at least two points."
        }

        geometry.forEach {
            require(
                it.latitude.isFinite() &&
                    it.latitude in -90.0..90.0
            )

            require(
                it.longitude.isFinite() &&
                    it.longitude in -180.0..180.0
            )
        }

        var previousBegin =
            0

        var previousEnd =
            0

        maneuvers.forEachIndexed {
                index,
                maneuver ->

            require(
                maneuver.endShapeIndex <
                    geometry.size
            ) {
                "Navigation maneuver shape index exceeds route geometry."
            }

            if (index > 0) {
                require(
                    maneuver.beginShapeIndex >=
                        previousBegin &&
                    maneuver.endShapeIndex >=
                        previousEnd
                ) {
                    "Navigation maneuvers are not route ordered."
                }
            }

            previousBegin =
                maneuver.beginShapeIndex

            previousEnd =
                maneuver.endShapeIndex
        }
    }
}

object NavigationRouteNativeCodec {
    fun encode(
        route: NavigationRouteContract,
    ): ByteArray {

        val output =
            ByteArrayOutputStream()

        DataOutputStream(
            output
        ).use {
                data ->

            data.writeInt(
                NATIVE_ROUTE_MAGIC
            )

            data.writeInt(
                route.schemaVersion
            )

            data.writeRouteString(
                route.routeId
            )

            data.writeInt(
                route.family.ordinal
            )

            data.writeDouble(
                route.distanceM
            )

            data.writeDouble(
                route.durationS
            )

            data.writeInt(
                route.geometry.size
            )

            route.geometry.forEach {
                    point ->

                data.writeDouble(
                    point.latitude
                )

                data.writeDouble(
                    point.longitude
                )
            }

            data.writeInt(
                route.maneuvers.size
            )

            route.maneuvers.forEach {
                    maneuver ->

                data.writeInt(
                    maneuver.type.ordinal
                )

                data.writeRouteString(
                    maneuver.instruction
                )

                data.writeInt(
                    maneuver.streetNames.size
                )

                maneuver.streetNames.forEach {
                        streetName ->

                    data.writeRouteString(
                        streetName
                    )
                }

                data.writeDouble(
                    maneuver.distanceM
                )

                data.writeDouble(
                    maneuver.durationS
                )

                data.writeInt(
                    maneuver.beginShapeIndex
                )

                data.writeInt(
                    maneuver.endShapeIndex
                )

                data.writeOptionalInt(
                    maneuver.bearingBeforeDeg
                )

                data.writeOptionalInt(
                    maneuver.bearingAfterDeg
                )

                data.writeOptionalInt(
                    maneuver.engineType
                )
            }

            data.writeRouteString(
                route.engineName
            )

            data.writeRouteString(
                route.engineVersion
            )

            data.writeInt(
                route.segmentDataStatus.ordinal
            )

            data.writeInt(
                route.diagnostics.size
            )

            route.diagnostics.forEach {
                    diagnostic ->

                data.writeRouteString(
                    diagnostic.code
                )

                data.writeRouteString(
                    diagnostic.message
                )
            }
        }

        val payload =
            output.toByteArray()

        require(
            payload.size <=
                MAX_NATIVE_ROUTE_PAYLOAD_BYTES
        ) {
            "Navigation route payload exceeds native transport limit."
        }

        return payload
    }
}

private fun DataOutputStream.writeRouteString(
    value: String,
) {
    val bytes =
        value.toByteArray(
            Charsets.UTF_8
        )

    require(
        bytes.size <=
            MAX_NATIVE_ROUTE_STRING_BYTES
    ) {
        "Navigation route string exceeds transport limit."
    }

    writeInt(
        bytes.size
    )

    write(
        bytes
    )
}

private fun DataOutputStream.writeOptionalInt(
    value: Int?,
) {
    writeBoolean(
        value != null
    )

    if (value != null) {
        writeInt(
            value
        )
    }
}

private const val NATIVE_ROUTE_MAGIC =
    0x4E525431

private const val MAX_NATIVE_ROUTE_STRING_BYTES =
    1_000_000

private const val MAX_NATIVE_ROUTE_PAYLOAD_BYTES =
    16 * 1024 * 1024