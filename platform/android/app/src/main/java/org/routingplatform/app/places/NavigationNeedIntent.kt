package org.routingplatform.app.places

/** Generic natural-language need used for all searchable route stops. */
data class NavigationNeedIntent(
    val query: String,
    val category: PoiCategory?,
    val alongRoute: Boolean = true,
    val maxDetourSeconds: Int = 900,
    val requireOpenNow: Boolean = false,
)

object NavigationNeedIntentParser {
    fun parse(text: String): NavigationNeedIntent? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null
        val category = when {
            listOf("wc", "toilette", "toiletten", "restroom").any(normalized::contains) -> PoiCategory.TOILET
            listOf("tankstelle", "diesel", "benzin", "kraftstoff").any(normalized::contains) -> PoiCategory.FUEL
            listOf("ladesÃ¤ule", "ladestation", "laden", "charging").any(normalized::contains) -> PoiCategory.CHARGING
            listOf("parkplatz", "parken").any(normalized::contains) -> PoiCategory.PARKING
            listOf("raststÃ¤tte", "rastplatz", "pause").any(normalized::contains) -> PoiCategory.REST_AREA
            listOf("bÃ¤cker", "bÃ¤ckerei", "essen", "restaurant").any(normalized::contains) -> PoiCategory.RESTAURANT
            listOf("apotheke").any(normalized::contains) -> PoiCategory.PHARMACY
            listOf("supermarkt", "einkaufen").any(normalized::contains) -> PoiCategory.SUPERMARKET
            listOf("werkstatt", "reparatur").any(normalized::contains) -> PoiCategory.WORKSHOP
            listOf("hotel", "Ã¼bernachtung").any(normalized::contains) -> PoiCategory.HOTEL
            else -> null
        }
        val openNow = listOf("offen", "geÃ¶ffnet", "jetzt").any(normalized::contains)
        val detour = when {
            listOf(
                "ohne großen umweg",
                "ohne grossen umweg",
                "kein großer umweg",
                "kein grosser umweg",
            ).any(normalized::contains) -> 900

            listOf(
                "ohne umweg",
                "kein umweg",
                "direkt",
            ).any(normalized::contains) ||
                (normalized.contains("ohne") && normalized.contains("umweg") &&
                    !normalized.contains("groß") &&
                    !normalized.contains("gross")) -> 300

            listOf(
                "kurzer umweg",
                "möglichst nah",
                "moeglichst nah",
            ).any(normalized::contains) -> 600

            else -> 900
        }
        return NavigationNeedIntent(
            query = text.trim().take(240),
            category = category,
            alongRoute = true,
            maxDetourSeconds = detour,
            requireOpenNow = openNow,
        )
    }

    fun toSearchRequest(intent: NavigationNeedIntent): PoiSearchRequest =
        PoiSearchRequest(
            query = intent.query,
            category = intent.category,
            alongRoute = intent.alongRoute,
            maxResults = 20,
        )
}

