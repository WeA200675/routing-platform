package org.routingplatform.app.navigation

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.valhalla.valhalla.Valhalla
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

        primaryHandle = primary.acquire(request) { firstResult ->
            if (cancelled.get() || closed.get()) return@acquire
            if (firstResult.isSuccess) {
                onResult(firstResult)
            } else {
                synchronized(lock) {
                    if (cancelled.get() || closed.get()) return@synchronized
                    fallbackHandle = fallback.acquire(request) { fallbackResult ->
                        if (!cancelled.get() && !closed.get()) onResult(fallbackResult)
                    }
                }
            }
        }

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
 * Android-local tile location. The tile archive is kept in app-private external
 * storage, where Android's storage quota is much larger than internal filesDir.
 */
object AndroidOfflineRoutingDatasetStore {
    const val TILE_ARCHIVE_NAME = "valhalla-dach-merged.tar"

    private fun directory(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "routing-data").apply { if (!exists()) mkdirs() }
    }

    fun tileArchive(context: Context): File =
        File(directory(context), TILE_ARCHIVE_NAME)

    fun metadataFile(context: Context): File =
        File(directory(context), TILE_ARCHIVE_NAME + ".properties")

    fun isInstalled(context: Context): Boolean {
        val archive = tileArchive(context)
        val metadata = metadataFile(context)
        if (!archive.isFile || archive.length() <= 0L || !metadata.isFile) return false
        return runCatching {
            val values = Properties().apply {
                FileInputStream(metadata).use { load(it) }
            }
            values.getProperty("schemaVersion") == "1" &&
                values.getProperty("sizeBytes") == archive.length().toString() &&
                values.getProperty("modifiedAt") == archive.lastModified().toString() &&
                values.getProperty("sha256")?.matches(Regex("[a-f0-9]{64}")) == true &&
                values.getProperty("valhallaVersion") == AndroidOfflineValhallaRouteSource.EXPECTED_VALHALLA_VERSION
        }.getOrDefault(false)
    }

    fun installedSummary(context: Context): String {
        if (!isInstalled(context)) return "DACH-Kartenpaket nicht installiert"
        val archive = tileArchive(context)
        val sizeGiB = archive.length().toDouble() / (1024.0 * 1024.0 * 1024.0)
        return "DACH-Kartenpaket bereit · " + String.format(java.util.Locale.GERMANY, "%.1f GB", sizeGiB)
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
        val raw = engine().routeRaw(buildRequestJson(request))
        return parseResponse(raw, request.family)
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
                throw UnsupportedOperationException(
                    "This route family needs platform-wide candidate scoring and is not " +
                        "approximated by on-device routing."
                )
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
            "\"directions_type\":\"instructions\"$optionsJson}"
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
            engineVersion = "3.9.0",
            segmentDataStatus = NavigationRouteSegmentDataStatus.Unspecified,
            diagnostics = listOf(
                NavigationRouteDiagnostic(
                    code = "on_device_route",
                    message = "Route computed locally from the installed DACH tile archive.",
                )
            ),
        )
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
        const val EXPECTED_VALHALLA_VERSION = "3.9.0"

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
