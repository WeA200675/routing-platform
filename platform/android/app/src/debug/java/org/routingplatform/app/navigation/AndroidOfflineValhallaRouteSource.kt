package org.routingplatform.app.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.valhalla.valhalla.Valhalla
import org.routingplatform.app.ai.JniSocialAiNativeEngine
import java.security.MessageDigest
import com.valhalla.valhalla.config.ValhallaConfigFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.Properties
import java.io.FileInputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the pre-existing HTTP service as a failover while preferring the
 * on-device Valhalla engine whenever a validated tile archive is installed.
 */
internal class FallbackNavigationRouteSource(
    private val primary: NavigationRouteSource,
    private val fallback: NavigationRouteSource,
) : NavigationRouteSource {
    private val closed = AtomicBoolean(false)

    override fun acquire(
        request: NavigationRouteRequest,
        onResult: (Result<NavigationRouteContract>) -> Unit,
    ): NavigationRouteAcquisitionHandle {
        check(!closed.get()) { "Navigation route source is closed." }
        val cancelled = AtomicBoolean(false)
        val lock = Any()
        var primaryHandle: NavigationRouteAcquisitionHandle? = null
        var fallbackHandle: NavigationRouteAcquisitionHandle? = null

        primaryHandle = primary.acquire(
            request = request,
            onResult = primaryResult@{ firstResult ->
                if (cancelled.get() || closed.get()) return@primaryResult
                if (firstResult.isSuccess) {
                    onResult(firstResult)
                } else {
                    synchronized(lock) {
                        if (cancelled.get() || closed.get()) return@synchronized
                        fallbackHandle = fallback.acquire(
                            request = request,
                            onResult = fallbackResult@{ result ->
                                if (!cancelled.get() && !closed.get()) onResult(result)
                            },
                        )
                    }
                }
            },
        )

        return object : NavigationRouteAcquisitionHandle {
            override fun cancel() {
                if (cancelled.compareAndSet(false, true)) {
                    synchronized(lock) {
                        primaryHandle?.cancel()
                        fallbackHandle?.cancel()
                    }
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            primary.close()
            fallback.close()
        }
    }
}

/**
 * Routes directly on the Pixel using Valhalla Mobile. It opens one native actor
 * lazily and reuses it for subsequent requests. A missing, invalid, unsupported,
 * or out-of-coverage local dataset fails to the HTTP source via the wrapper.
 */
internal class AndroidOfflineValhallaRouteSource(
    context: Context,
) : NavigationRouteSource {
    private val appContext = context.applicationContext
    private val tileArchive = AndroidOfflineRoutingDatasetStore.tileArchive(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val engineLock = Any()

    @Volatile
    private var engine: Valhalla? = null

    override fun acquire(
        request: NavigationRouteRequest,
        onResult: (Result<NavigationRouteContract>) -> Unit,
    ): NavigationRouteAcquisitionHandle {
        check(!closed.get()) { "Navigation route source is closed." }
        val cancelled = AtomicBoolean(false)
        val future: Future<*> = executor.submit {
            val result = runCatching { route(request) }
            mainHandler.post {
                if (!cancelled.get() && !closed.get()) onResult(result)
            }
        }
        return object : NavigationRouteAcquisitionHandle {
            override fun cancel() {
                cancelled.set(true)
                future.cancel(true)
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
            synchronized(engineLock) {
                engine?.close()
                engine = null
                engineDatasetKey = null
            }
        }
    }

    private fun route(request: NavigationRouteRequest): NavigationRouteContract {
        check(AndroidOfflineRoutingDatasetStore.isInstalled(appContext)) {
            "On-device DACH routing data is not installed or failed its integrity checks."
        }
        val valhalla = engine()
        val usesStandardProfileFallback = request.family in setOf(
            NavigationRouteFamily.LowUrban,
            NavigationRouteFamily.LowCurvature,
            NavigationRouteFamily.LowGradient,
            NavigationRouteFamily.LowTraffic,
            NavigationRouteFamily.Energy,
            NavigationRouteFamily.Scenic,
            NavigationRouteFamily.Stable,
        )
        val baseline = parseResponse(valhalla.routeRaw(buildRequestJson(request)), request.family)
        val parsed = chooseAiRoute(valhalla, request, baseline) ?: baseline.copy(
            diagnostics = baseline.diagnostics + NavigationRouteDiagnostic(
                "on_device_ai_route_advisor_fallback",
                "KI-Routenvergleich nicht verfügbar; die geprüfte Valhalla-Grundroute bleibt aktiv.",
            ),
        )
        val route = if (usesStandardProfileFallback) {
            parsed.copy(
                diagnostics = parsed.diagnostics + NavigationRouteDiagnostic(
                    "offline_route_profile_fallback",
                    "Lokale Grundroute aktiv: Das Profil „${request.family}“ wird offline nicht optimiert. Bitte die Route prüfen; die gewünschte Präferenz ist nicht garantiert.",
                ),
            )
        } else parsed
        return attachSpeedLimitAttributes(valhalla, route)
    }


    private fun chooseAiRoute(
        valhalla: Valhalla,
        request: NavigationRouteRequest,
        baseline: NavigationRouteContract,
    ): NavigationRouteContract? = runCatching {
        val model = File(appContext.filesDir, "g620-qwen2.5-1.5b-q4_k_m.gguf")
        require(model.isFile && model.length() in 100_000_000L..3_000_000_000L)
        require(sha256(model) == "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370")
        val learnedFamily = NavigationRoutePreferenceLearningStore.preferredFamily(appContext)?.first
        val families = listOfNotNull(request.family, learnedFamily, NavigationRouteFamily.Fastest,
            NavigationRouteFamily.Shortest, NavigationRouteFamily.MajorRoads, NavigationRouteFamily.Comfort)
            .distinct().take(4)
        val candidates = families.mapIndexedNotNull { id, family ->
            runCatching {
                val route = if (family == request.family) baseline else
                    parseResponse(valhalla.routeRaw(buildRequestJson(request.copy(family = family))), family)
                require(route.geometry.size >= 2 && route.distanceM.isFinite() && route.durationS.isFinite())
                require(distanceMeters(route.geometry.first(), request.origin) <= 2_500.0)
                require(distanceMeters(route.geometry.last(), request.destination) <= 2_500.0)
                id to route
            }.getOrNull()
        }
        require(candidates.size >= 2)
        val engine = JniSocialAiNativeEngine()
        try {
            require(engine.loadModel(model.absolutePath, 4096))
            val options = candidates.joinToString("\n") { (id, route) ->
                "Kandidat $id: Profil=${route.family.name}, Distanz=${route.distanceM.toLong()}m, Zeit=${route.durationS.toLong()}s, Abbiegungen=${route.maneuvers.size}"
            }
            val prompt = "<|im_start|>system\nVergleiche nur diese graphberechneten Routen. Wähle eine Kandidaten-ID. Erfinde keine Fakten. Gewünschtes Profil: ${request.family.name}. Antworte ausschließlich JSON: {\"candidate\":0,\"reason\":\"balanced\"}. reason ist fastest, shortest, major_roads, comfort oder balanced.\n<|im_end|>\n<|im_start|>user\n$options\n<|im_end|>\n<|im_start|>assistant\n"
            val learned = NavigationRoutePreferenceLearningStore.preferredFamily(appContext)
                ?.let { "Bisheriges explizites Fahrerfeedback: Profil ${it.first.name} (${it.second}). Berücksichtige es als Präferenz, sofern die Route vergleichbar bleibt." }
                ?: "Noch kein ausreichendes Fahrerfeedback gespeichert."
            val promptWithLearning = prompt.replace(options, "$options\\nBisheriges Fahrerfeedback: $learned")
            val raw = engine.generate(promptWithLearning, 96)
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            require(start >= 0 && end > start)
            val choice = JSONObject(raw.substring(start, end + 1))
            val id = choice.getInt("candidate")
            val reason = choice.optString("reason")
            require(reason in setOf("fastest", "shortest", "major_roads", "comfort", "balanced"))
            val aiSelected = candidates.firstOrNull { it.first == id }?.second ?: error("Unknown candidate")
            val preferredRoute = candidates.firstOrNull { it.second.family == learnedFamily }?.second
            val selected = if (
                preferredRoute != null &&
                aiSelected.family != preferredRoute.family &&
                aiSelected.durationS > preferredRoute.durationS * 0.85 &&
                aiSelected.distanceM > preferredRoute.distanceM * 0.85
            ) preferredRoute else aiSelected
            selected.copy(diagnostics = selected.diagnostics + listOf(
                NavigationRouteDiagnostic(
                    "on_device_ai_route_advisor",
                    "Lokale KI verglich ${candidates.size} Valhalla-Routen und wählte Profil ${selected.family.name} ($reason).",
                ),
                NavigationRouteDiagnostic(
                    "on_device_ai_route_family_${selected.family.name}",
                    "Interne Zuordnung für explizites lokales Routenfeedback.",
                ),
            ))
        } finally {
            runCatching { engine.unloadModel() }
        }
    }.getOrNull()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * radius * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }

    /**
     * Read speed-limit attributes from the same on-device DACH graph.
     * Requests are deliberately chunked so long routes never create a
     * single oversized trace_attributes map-match on the Android stack.
     * Speed metadata is advisory presentation data: failure never blocks
     * a valid route.
     */
    private fun attachSpeedLimitAttributes(
        valhalla: Valhalla,
        route: NavigationRouteContract,
    ): NavigationRouteContract {
        val segments = mutableListOf<NavigationSpeedLimitSegment>()
        var failedChunks = 0
        var chunkStart = 0
        while (chunkStart < route.geometry.lastIndex) {
            val chunkEnd = (chunkStart + TRACE_ATTRIBUTE_CHUNK_POINTS)
                .coerceAtMost(route.geometry.lastIndex)
            val shape = JSONArray()
            for (index in chunkStart..chunkEnd) {
                val point = route.geometry[index]
                shape.put(
                    JSONObject()
                        .put("lat", point.latitude)
                        .put("lon", point.longitude)
                )
            }
            val filters =
                JSONObject()
                    .put("action", "include")
                    .put(
                        "attributes",
                        JSONArray()
                            .put("edge.speed_limit")
                            .put("edge.is_urban")
                            .put("edge.begin_shape_index")
                            .put("edge.end_shape_index"),
                    )
            val request =
                JSONObject()
                    .put("shape", shape)
                    .put("costing", "auto")
                    .put("units", "kilometers")
                    .put("shape_match", "walk_or_snap")
                    .put("filters", filters)

            val chunkResult = runCatching {
                val response = JSONObject(valhalla.traceAttributesRaw(request.toString()))
                val edges = response.optJSONArray("edges")
                    ?: error("Valhalla trace_attributes returned no edges array.")
                for (edgeIndex in 0 until edges.length()) {
                    val edge = edges.getJSONObject(edgeIndex)
                    val begin = edge.optInt("begin_shape_index", -1)
                    val end = edge.optInt("end_shape_index", -1)
                    if (begin < 0 || end < begin || end > chunkEnd - chunkStart) continue

                    val rawLimit = edge.opt("speed_limit")
                    val unlimited = rawLimit is String &&
                        rawLimit.equals("unlimited", ignoreCase = true)
                    val numericLimit = when (rawLimit) {
                        is Number -> rawLimit.toDouble()
                        is String -> rawLimit.toDoubleOrNull()
                        else -> null
                    }?.takeIf { it.isFinite() && it in 1.0..300.0 }
                    segments += NavigationSpeedLimitSegment(
                        beginShapeIndex = chunkStart + begin,
                        endShapeIndex = chunkStart + end,
                        speedLimitKmh = numericLimit,
                        unlimited = unlimited,
                        isUrban = edge.opt("is_urban") as? Boolean,
                    )
                }
            }
            if (chunkResult.isFailure) failedChunks += 1
            chunkStart = chunkEnd
        }

        val distinctSegments = segments
            .distinctBy { it.beginShapeIndex to it.endShapeIndex }
            .sortedWith(compareBy<NavigationSpeedLimitSegment> { it.beginShapeIndex }.thenBy { it.endShapeIndex })
        val status = when {
            distinctSegments.isEmpty() -> NavigationSpeedLimitDataStatus.Unavailable
            failedChunks > 0 -> NavigationSpeedLimitDataStatus.Partial
            else -> NavigationSpeedLimitDataStatus.Complete
        }
        val diagnostic = when (status) {
            NavigationSpeedLimitDataStatus.Complete ->
                NavigationRouteDiagnostic(
                    "speed_limit_attributes_complete",
                    "Speed-limit attributes were read from the installed on-device map tiles.",
                )
            NavigationSpeedLimitDataStatus.Partial ->
                NavigationRouteDiagnostic(
                    "speed_limit_attributes_partial",
                    "Some route sections have no speed-limit attribute result.",
                )
            NavigationSpeedLimitDataStatus.Unavailable ->
                NavigationRouteDiagnostic(
                    "speed_limit_attributes_unavailable",
                    "No reliable speed-limit attributes were returned for this route.",
                )
        }
        return route.copy(
            speedLimitSegments = distinctSegments,
            speedLimitDataStatus = status,
            diagnostics = route.diagnostics + diagnostic,
        )
    }

    private fun engine(): Valhalla =
        synchronized(engineLock) {
            val metadata = AndroidOfflineRoutingDatasetStore.metadataFile(appContext)
            val datasetKey = metadata.readText().hashCode().toString()
            if (engineDatasetKey != datasetKey) {
                engine?.close()
                engine = null
                engineDatasetKey = datasetKey
            }
            engine ?: Valhalla(
                appContext,
                ValhallaConfigFactory.usingTileExtract(tileArchive.absolutePath),
            ).also { engine = it }
        }

    @Volatile
    private var engineDatasetKey: String? = null

    private fun buildRequestJson(request: NavigationRouteRequest): String {
        val options = when (request.family) {
            NavigationRouteFamily.Fastest,
            NavigationRouteFamily.ProfileOptimal -> ""

            NavigationRouteFamily.Shortest -> "\"shortest\":true"

            NavigationRouteFamily.MajorRoads -> "\"use_highways\":0.9"

            NavigationRouteFamily.Comfort ->
                "\"use_tracks\":0,\"use_living_streets\":0," +
                    "\"service_factor\":3,\"maneuver_penalty\":25"

            NavigationRouteFamily.LowUrban,
            NavigationRouteFamily.LowCurvature,
            NavigationRouteFamily.LowGradient,
            NavigationRouteFamily.LowTraffic,
            NavigationRouteFamily.Energy,
            NavigationRouteFamily.Scenic,
            NavigationRouteFamily.Stable ->
                ""
        }

        val locations = buildList {
            add(request.origin)
            addAll(request.viaPoints)
            add(request.destination)
        }.joinToString(",") { point ->
            "{\"lat\":" + point.latitude + ",\"lon\":" + point.longitude + "}"
        }

        val optionsJson =
            if (options.isBlank()) "" else ",\"costing_options\":{\"auto\":{$options}}"

        return "{\"locations\":[$locations],\"costing\":\"auto\"," +
            "\"units\":\"kilometers\",\"language\":\"de-DE\"," +
            "\"directions_type\":\"instructions\",\"turn_lanes\":true$optionsJson}"
    }

    private fun parseResponse(
        response: String,
        family: NavigationRouteFamily,
    ): NavigationRouteContract {
        val root = JSONObject(response)
        val trip = root.getJSONObject("trip")
        val legs = trip.getJSONArray("legs")
        val geometry = mutableListOf<RoutePoint>()
        val maneuvers = mutableListOf<NavigationRouteContractManeuver>()

        for (legIndex in 0 until legs.length()) {
            val leg = legs.getJSONObject(legIndex)
            val legGeometry = decodePolyline6(leg.getString("shape"))
            require(legGeometry.size >= 2) { "Valhalla returned a short route leg." }

            val offset = if (geometry.isEmpty()) 0 else geometry.lastIndex
            if (geometry.isEmpty()) {
                geometry.addAll(legGeometry)
            } else {
                geometry.addAll(legGeometry.drop(1))
            }

            val legManeuvers = leg.getJSONArray("maneuvers")
            for (maneuverIndex in 0 until legManeuvers.length()) {
                val source = legManeuvers.getJSONObject(maneuverIndex)
                val names = source.optJSONArray("street_names").toStringList()
                val rawType = source.optInt("type", 0)
                val instruction = source.optString("instruction").trim()
                val laneIndications = parseLaneIndications(source, rawType)
                val destinationSign = parseDestinationSign(source)
                maneuvers += NavigationRouteContractManeuver(
                    type = mapManeuverType(rawType),
                    instruction = instruction.ifBlank { "Weiterfahren" },
                    streetNames = names.filter(String::isNotBlank),
                    distanceM = source.optDouble("length", 0.0) * 1000.0,
                    durationS = source.optDouble("time", 0.0),
                    beginShapeIndex = offset + source.getInt("begin_shape_index"),
                    endShapeIndex = offset + source.getInt("end_shape_index"),
                    bearingBeforeDeg = source.optionalHeading("begin_heading"),
                    bearingAfterDeg = source.optionalHeading("end_heading"),
                    engineType = rawType,
                    destinationSign = destinationSign,
                    laneIndications = laneIndications,
                )
            }
        }

        val summary = trip.getJSONObject("summary")
        val routeDistanceM = summary.getDouble("length") * 1000.0
        val routeDurationS = summary.getDouble("time")
        require(geometry.size >= 2) { "Valhalla returned no route geometry." }

        return NavigationRouteContract(
            routeId = "valhalla-mobile-" + UUID.randomUUID().toString(),
            family = family,
            distanceM = routeDistanceM,
            durationS = routeDurationS,
            geometry = geometry,
            maneuvers = maneuvers,
            engineName = "Valhalla Mobile",
            engineVersion = "3.9.1",
            segmentDataStatus = NavigationRouteSegmentDataStatus.Unspecified,
            diagnostics = listOf(
                NavigationRouteDiagnostic(
                    code = "on_device_route",
                    message = "Route computed locally from the installed DACH tile archive.",
                )
            ),
        )
    }


    private fun parseLaneIndications(maneuver: JSONObject, maneuverType: Int): List<String> {
        val lanes = maneuver.optJSONArray("lanes") ?: return emptyList()
        val directionBits = listOf(
            4 to "↖", 8 to "←", 16 to "↙",
            2 to "↑",
            32 to "↘", 64 to "→", 128 to "↗",
            256 to "↶", 512 to "↖", 1024 to "↗",
        )
        val targetDirections = when (maneuverType) {
            9, 10, 11, 18, 20, 23, 37 -> 32 or 64 or 128 or 1024
            12, 13 -> 256
            14, 15, 16, 19, 21, 24, 38 -> 4 or 8 or 16 or 512
            1, 7, 8, 17, 22, 25, 28, 29, 30, 31, 32, 33, 34, 35, 36, 39, 40, 41, 42, 43 -> 2
            else -> 0
        }
        return buildList {
            for (index in 0 until lanes.length()) {
                val lane = lanes.optJSONObject(index) ?: continue
                val directions = lane.optInt("directions", 0)
                val valid = lane.optInt("valid", 0)
                val active = lane.optInt("active", 0)
                val arrows = directionBits.filter { (bit, _) -> (directions and bit) != 0 }
                    .map { it.second }.distinct().joinToString(" ")
                    .ifBlank { "?" }
                val isActive = targetDirections != 0 && (active and targetDirections) != 0
                val isValid = targetDirections != 0 && (valid and targetDirections) != 0 || isActive
                add(
                    "Spur ${index + 1}: $arrows" +
                        (if (isActive) " ★" else if (isValid) " ✓" else "")
                )
            }
        }
    }

    private fun parseDestinationSign(maneuver: JSONObject): String? {
        val sign = maneuver.optJSONObject("sign") ?: return null
        val keys = listOf(
            "exit_number_elements",
            "exit_branch_elements",
            "exit_toward_elements",
            "exit_name_elements",
        )
        val text = keys.flatMap { key ->
            val elements = sign.optJSONArray(key) ?: return@flatMap emptyList()
            (0 until elements.length()).mapNotNull { index ->
                elements.optJSONObject(index)?.optString("text")?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }.distinct().joinToString(" · ").take(160)
        return text.takeIf { it.isNotBlank() }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) add(optString(index).trim())
        }
    }

    private fun JSONObject.optionalHeading(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key).takeIf { it in 0..359 }
    }

    private fun decodePolyline6(encoded: String): List<RoutePoint> {
        val result = ArrayList<RoutePoint>()
        var index = 0
        var latitude = 0
        var longitude = 0

        while (index < encoded.length) {
            val latValue = decodePolylineValue(encoded, index)
            latitude += latValue.first
            index = latValue.second
            val lonValue = decodePolylineValue(encoded, index)
            longitude += lonValue.first
            index = lonValue.second
            result += RoutePoint(latitude / 1_000_000.0, longitude / 1_000_000.0)
        }

        return result
    }

    private fun decodePolylineValue(encoded: String, startIndex: Int): Pair<Int, Int> {
        var index = startIndex
        var result = 0
        var shift = 0
        while (true) {
            require(index < encoded.length) { "Truncated Valhalla polyline." }
            val value = encoded[index++].code - 63
            require(value in 0..63) { "Invalid Valhalla polyline character." }
            result = result or ((value and 0x1f) shl shift)
            shift += 5
            require(shift <= 30) { "Valhalla polyline value overflow." }
            if (value < 0x20) break
        }
        val delta = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        return delta to index
    }

    private fun mapManeuverType(type: Int): ManeuverType =
        when (type) {
            1, 2, 3 -> ManeuverType.Start
            4, 5, 6 -> ManeuverType.Arrive
            8, 17, 22, 28, 29, 30, 31, 32, 33, 34, 35, 36, 39, 40, 41, 42, 43 ->
                ManeuverType.Continue
            9, 10, 11, 18, 20, 23, 37 -> ManeuverType.TurnRight
            12, 13 -> ManeuverType.UTurn
            14, 15, 16, 19, 21, 24, 38 -> ManeuverType.TurnLeft
            25 -> ManeuverType.Merge
            26 -> ManeuverType.RoundaboutEnter
            27 -> ManeuverType.RoundaboutExit
            7 -> ManeuverType.Continue
            else -> ManeuverType.Unknown
        }

    companion object {
        const val EXPECTED_VALHALLA_VERSION = "3.9.1"

        fun configured(
            context: Context,
            endpointText: String?,
        ): NavigationRouteSource {
            val offline = AndroidOfflineValhallaRouteSource(context)
            val endpoint = endpointText?.trim()?.takeIf(String::isNotEmpty)
            if (endpoint == null) return offline
            val remote = AndroidHttpNavigationRouteSource(context, URI(endpoint))
            return FallbackNavigationRouteSource(offline, remote)
        }
    }
}

private const val TRACE_ATTRIBUTE_CHUNK_POINTS = 128
