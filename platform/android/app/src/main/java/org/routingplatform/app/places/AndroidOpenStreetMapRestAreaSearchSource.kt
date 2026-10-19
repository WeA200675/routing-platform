package org.routingplatform.app.places

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.routingplatform.app.navigation.RoutePoint

/** Explicit rest-stop search. OSM only; unknown toilet data is never treated as absence. */
class AndroidOpenStreetMapRestAreaSearchSource : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val lastRequestAt = AtomicLong(0L)

    fun searchNear(
        query: String,
        center: RoutePoint,
        onResult: (Result<List<DestinationSearchResult>>) -> Unit,
    ): DestinationSearchHandle {
        check(!closed.get())
        val filter = parseRoadsideStopQuery(query)
            ?: throw IllegalArgumentException("Query is not a roadside-stop search.")
        val cancelled = AtomicBoolean(false)
        val future: Future<*> = executor.submit {
            val result = runCatching { lookup(RoadsideStopSearchRequest(center, filter)) }
            mainHandler.post {
                if (!cancelled.get() && !closed.get()) onResult(result)
            }
        }
        return object : DestinationSearchHandle {
            override fun cancel() {
                cancelled.set(true)
                future.cancel(true)
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdownNow()
    }

    private fun lookup(request: RoadsideStopSearchRequest): List<DestinationSearchResult> {
        val now = System.currentTimeMillis()
        val previous = lastRequestAt.get()
        if (now - previous < MIN_REQUEST_INTERVAL_MS ||
            !lastRequestAt.compareAndSet(previous, now)
        ) {
            throw IllegalStateException("Bitte kurz warten, bevor du erneut nach Rastplätzen suchst.")
        }
        val lat = request.center.latitude
        val lon = request.center.longitude
        val query = """
            [out:json][timeout:20];
            (
              nwr(around:45000,$lat,$lon)[highway~"^(services|rest_area)$"];
              nwr(around:45000,$lat,$lon)[amenity="rest_area"];
            );
            out center tags 80;
        """.trimIndent()
        val connection = (URL(OVERPASS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val encoded = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            connection.outputStream.use { it.write(encoded.toByteArray(StandardCharsets.UTF_8)) }
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("OpenStreetMap search returned HTTP ${connection.responseCode}.")
            }
            val body = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_RESPONSE_BYTES) { "Rest-stop response is too large." }
                    output.write(buffer, 0, count)
                }
                output.toString(StandardCharsets.UTF_8.name())
            }
            return parseElements(body, request).take(MAX_DESTINATION_SEARCH_RESULTS)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseElements(
        body: String,
        request: RoadsideStopSearchRequest,
    ): List<DestinationSearchResult> {
        val payload = JSONObject(body)
        payload.optString("remark").takeIf(String::isNotBlank)?.let {
            throw IllegalStateException("OpenStreetMap search provider reported an error.")
        }
        val elements = payload.optJSONArray("elements") ?: return emptyList()
        val results = buildList {
            for (index in 0 until minOf(elements.length(), MAX_OSM_ELEMENTS)) {
                val element = elements.optJSONObject(index) ?: continue
                val tagsJson = element.optJSONObject("tags") ?: continue
                val tags = buildMap {
                    val keys = tagsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, tagsJson.optString(key))
                    }
                }
                if (!RoadsideStopTagParser.isRestArea(tags)) continue
                val restroom = RoadsideStopTagParser.restroomStatus(tags)
                if (!RoadsideStopTagParser.matches(restroom, request.restroomFilter)) continue
                val pointJson = if (element.has("lat") && element.has("lon")) {
                    element
                } else {
                    element.optJSONObject("center") ?: continue
                }
                val lat = pointJson.optDouble("lat", Double.NaN)
                val lon = pointJson.optDouble("lon", Double.NaN)
                if (!lat.isFinite() || lat !in -90.0..90.0 ||
                    !lon.isFinite() || lon !in -180.0..180.0
                ) continue
                val type = element.optString("type", "")
                val osmId = element.optLong("id", -1L)
                if (type !in setOf("node", "way", "relation") || osmId <= 0L) continue
                val name = tags["name"]?.takeIf(String::isNotBlank)
                    ?: tags["brand"]?.takeIf(String::isNotBlank)
                    ?: "Raststätte / Rastplatz"
                add(
                    RoadsideStop(
                        id = "osm:$type:$osmId",
                        name = name.take(180),
                        point = RoutePoint(lat, lon),
                        restroomStatus = restroom,
                        wheelchairAccessible = RoadsideStopTagParser.wheelchairAccessible(tags),
                    ).toDestinationResult(),
                )
            }
        }
        return results.distinctBy(DestinationSearchResult::id)
    }

    private companion object {
        const val OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
        const val USER_AGENT = "RoutingPlatform/0.1 (Android roadside stop search)"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 25_000
        const val MAX_RESPONSE_BYTES = 2_000_000
        const val MAX_OSM_ELEMENTS = 160
        const val MIN_REQUEST_INTERVAL_MS = 10_000L
    }
}
