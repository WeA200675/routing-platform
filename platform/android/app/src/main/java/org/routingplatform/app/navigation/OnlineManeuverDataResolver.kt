package org.routingplatform.app.navigation

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.*

/** Fetches missing lane metadata from OSM and applies it only to an exact, direction-matched road. */
internal object OnlineManeuverDataResolver {
    private const val RADIUS_M = 18.0
    private const val MAX_TARGETS = 12
    private val endpoints = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")
    private data class Target(val index: Int, val point: RoutePoint, val heading: Int, val names: Set<String>)
    private data class Way(val tags: Map<String, String>, val points: List<RoutePoint>)
    private data class Match(val way: Way, val forward: Boolean, val score: Double)

    fun enrich(route: NavigationRouteContract): NavigationRouteContract {
        val relevant = route.maneuvers.filter { m ->
            m.type in setOf(ManeuverType.Exit, ManeuverType.Merge, ManeuverType.TurnLeft, ManeuverType.TurnRight) &&
                (m.laneIndications.isEmpty() || m.destinationSign.isNullOrBlank())
        }
        val lookupNeeded = relevant.size
        val targets = route.maneuvers.mapIndexedNotNull { i, m ->
            if (m.type !in setOf(ManeuverType.Exit, ManeuverType.Merge, ManeuverType.TurnLeft, ManeuverType.TurnRight)) return@mapIndexedNotNull null
            if (m.laneIndications.isNotEmpty() && !m.destinationSign.isNullOrBlank()) return@mapIndexedNotNull null
            val heading = m.bearingBeforeDeg ?: return@mapIndexedNotNull null
            val names = m.streetNames.map(::norm).filter(String::isNotBlank).toSet()
            if (names.isEmpty()) return@mapIndexedNotNull null
            Target(i, route.geometry[m.beginShapeIndex.coerceIn(0, route.geometry.lastIndex)], heading, names)
        }.take(MAX_TARGETS)
        if (targets.isEmpty()) {
            if (lookupNeeded == 0) return route
            return route.copy(
                diagnostics = route.diagnostics + NavigationRouteDiagnostic(
                    "online_maneuver_data_osm_unavailable",
                    "Onlineabgleich nicht gestartet: Straßenname oder Fahrtrichtung fehlt für einen eindeutigen Kartenabgleich.",
                ),
            )
        }
        val ways = fetch(targets) ?: return route.copy(
            diagnostics = route.diagnostics + NavigationRouteDiagnostic(
                "online_maneuver_data_osm_unavailable",
                "OpenStreetMap-Onlineabgleich nicht erreichbar; fehlende Spur- und Kartenzieldaten bleiben nicht verfügbar.",
            ),
        )
        var laneCount = 0
        var destinationCount = 0
        val updated = route.maneuvers.mapIndexed { index, m ->
            val target = targets.firstOrNull { it.index == index } ?: return@mapIndexed m
            val match = match(target, ways) ?: return@mapIndexed m
            val laneData = if (m.laneIndications.isEmpty()) parseLanes(match.way.tags, match.forward, m.type, m.maneuverSide) else null
            val destination = if (m.destinationSign.isNullOrBlank()) parseDestination(match.way.tags) else null
            if (!laneData.isNullOrEmpty()) laneCount++
            if (!destination.isNullOrBlank()) destinationCount++
            m.copy(
                laneIndications = laneData ?: m.laneIndications,
                destinationSign = destination?.let { "OSM-Kartenziel: $it" } ?: m.destinationSign,
            )
        }
        val message = if (laneCount + destinationCount > 0)
            "OpenStreetMap geprüft · ${targets.size} von $lookupNeeded Abbiegepunkten abgefragt; $laneCount Spurhinweis(e), $destinationCount Kartenziel(e) passend abgeglichen. © OpenStreetMap-Mitwirkende."
        else
            "OpenStreetMap geprüft · ${targets.size} von $lookupNeeded Abbiegepunkten abgefragt; keine passend verifizierten Spur- oder Kartenzieldaten gefunden. © OpenStreetMap-Mitwirkende."
        return route.copy(
            maneuvers = updated,
            diagnostics = route.diagnostics.filterNot { it.code.startsWith("online_maneuver_data_osm_") } +
                NavigationRouteDiagnostic("online_maneuver_data_osm_checked", message),
        )
    }

    private fun fetch(targets: List<Target>): List<Way>? {
        val selectors = targets.flatMap { t ->
            val lat = "%.6f".format(java.util.Locale.ROOT, t.point.latitude)
            val lon = "%.6f".format(java.util.Locale.ROOT, t.point.longitude)
            listOf("turn:lanes", "turn:lanes:forward", "turn:lanes:backward", "destination",
                "destination:ref", "destination:lanes").map { key ->
                "way(around:$RADIUS_M,$lat,$lon)[\"highway\"][\"$key\"];"
            }
        }.distinct()
        val query = "[out:json][timeout:3];(" + selectors.joinToString("") + ");out tags geom;"
        for (endpoint in endpoints) {
            val raw = runCatching {
                val c = URL(endpoint).openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 1200
                    c.readTimeout = 1800
                    c.doOutput = true
                    c.setRequestProperty("User-Agent", "RoutingPlatform/1.0 (OSM lane metadata)")
                    c.setRequestProperty("Accept", "application/json")
                    c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    c.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8)) }
                    check(c.responseCode in 200..299)
                    c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText().take(1_500_001) }
                } finally { c.disconnect() }
            }.getOrNull() ?: continue
            val parsed = parseWays(raw)
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseWays(raw: String): List<Way>? = runCatching {
        val rows = JSONObject(raw).getJSONArray("elements")
        require(rows.length() <= 500)
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONObject(i)
            val tagJson = row.optJSONObject("tags") ?: return@mapNotNull null
            val geometry = row.optJSONArray("geometry") ?: return@mapNotNull null
            val tags = buildMap {
                val keys = tagJson.keys()
                while (keys.hasNext()) { val k = keys.next(); put(k, tagJson.optString(k)) }
            }
            val points = (0 until geometry.length()).mapNotNull { j ->
                geometry.optJSONObject(j)?.let { RoutePoint(it.optDouble("lat", Double.NaN), it.optDouble("lon", Double.NaN)) }
                    ?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() }
            }
            if (tags["highway"].isNullOrBlank() || points.size < 2) null else Way(tags, points)
        }
    }.getOrNull()

    private fun match(target: Target, ways: List<Way>): Match? {
        val found = ways.mapNotNull { way ->
            val roadTags = listOfNotNull(way.tags["name"], way.tags["ref"]).flatMap { it.split(';') }.map(::norm)
            if (roadTags.none { tag -> target.names.any { it == tag || it.contains(tag) || tag.contains(it) } }) return@mapNotNull null
            var best = Double.POSITIVE_INFINITY
            var forward = true
            for (i in 0 until way.points.lastIndex) {
                val a = way.points[i]; val b = way.points[i + 1]
                val distance = segmentDistance(target.point, a, b)
                if (distance > RADIUS_M || distance >= best) continue
                val h = bearing(a, b)
                val f = angle(h, target.heading); val r = angle((h + 180) % 360, target.heading)
                if (min(f, r) > 25.0) continue
                best = distance + min(f, r) * 0.08
                forward = f <= r
            }
            if (best.isFinite()) Match(way, forward, best) else null
        }.sortedBy { it.score }
        val first = found.firstOrNull() ?: return null
        if (found.getOrNull(1)?.let { it.score - first.score < 3.0 } == true) return null
        return first
    }

    internal fun parseLanes(\n        tags: Map<String, String>,\n        forward: Boolean,\n        type: ManeuverType,\n        side: NavigationManeuverSide? = null,\n    ): List<String>? {
        val directional = if (forward) "turn:lanes:forward" else "turn:lanes:backward"
        val value = tags[directional] ?: tags["turn:lanes"]?.takeIf {
            tags["oneway"] in setOf("yes", "1", "-1")
        } ?: return null
        val leftTokens = setOf("left", "slight_left", "sharp_left", "reverse", "merge_to_left")
        val rightTokens = setOf("right", "slight_right", "sharp_right", "merge_to_right")
        val desired = when (type) {
            ManeuverType.TurnLeft -> leftTokens
            ManeuverType.TurnRight -> rightTokens
            ManeuverType.Exit, ManeuverType.Merge -> when (side) {
                NavigationManeuverSide.Left -> leftTokens
                NavigationManeuverSide.Right -> rightTokens
                else -> emptySet()
            }
            else -> emptySet()
        }
        val lanes = value.split('|').take(12)
        if (lanes.isEmpty() || lanes.all { it.isBlank() || it == "none" }) return null
        return lanes.mapIndexed { i, lane ->
            val tokens = lane.split(';').map { it.trim().lowercase() }
            val arrows = tokens.mapNotNull {
                when (it) {
                    "left" -> "←"; "slight_left", "merge_to_left" -> "↖"; "sharp_left" -> "↙"
                    "through" -> "↑"; "right" -> "→"; "slight_right", "merge_to_right" -> "↗"
                    "sharp_right" -> "↘"; "reverse" -> "↶"; "none" -> "?"
                    else -> null
                }
            }.distinct().joinToString(" ").ifBlank { "?" }
            "Spur ${i + 1}: $arrows" + (if (tokens.any { it in desired }) " ✓" else "") + " · OSM"
        }
    }

    private fun parseDestination(tags: Map<String, String>): String? =
        listOfNotNull(tags["destination:ref"], tags["destination"], tags["destination:street"])
            .flatMap { it.split(';') }.map(String::trim).filter { it.isNotBlank() && it.length <= 80 }
            .distinct().take(3).joinToString(" · ").take(150).takeIf(String::isNotBlank)

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9äöüß]"), "")
    private fun angle(a: Int, b: Int): Double { val d = abs(a - b) % 360; return min(d, 360 - d).toDouble() }
    private fun bearing(a: RoutePoint, b: RoutePoint): Int {
        val p1 = Math.toRadians(a.latitude); val p2 = Math.toRadians(b.latitude)
        val dl = Math.toRadians(b.longitude - a.longitude)
        return ((Math.toDegrees(atan2(sin(dl) * cos(p2), cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl))) + 360) % 360).toInt()
    }
    private fun segmentDistance(p: RoutePoint, a: RoutePoint, b: RoutePoint): Double {
        val lat = Math.toRadians((a.latitude + b.latitude + p.latitude) / 3)
        val sx = 111320.0 * cos(lat); val sy = 111320.0
        val ax = a.longitude * sx; val ay = a.latitude * sy
        val bx = b.longitude * sx; val by = b.latitude * sy
        val px = p.longitude * sx; val py = p.latitude * sy
        val dx = bx - ax; val dy = by - ay; val d = dx * dx + dy * dy
        val t = if (d == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / d).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }
}
