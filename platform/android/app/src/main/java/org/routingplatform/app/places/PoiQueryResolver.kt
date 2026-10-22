package org.routingplatform.app.places

/**
 * Controlled POI query understanding. Classification is advisory: it may
 * suggest tags/categories, but only confirmed map results can become a route.
 */
internal enum class PoiCategory(val title: String, val osmQuery: String) {
    Fuel("Tankstellen", "[amenity=fuel]"),
    Charging("Ladestationen", "[amenity=charging_station]"),
    Parking("Parkplätze", "[amenity=parking]"),
    Restroom("WC & Toiletten", "[amenity=toilets]"),
    RestArea("Raststätten & Rastplätze", "[highway~\"^(services|rest_area)$\"]"),
    Food("Restaurants & Essen", "[amenity~\"^(restaurant|fast_food|cafe)$\"]"),
    Shop("Geschäfte & Supermärkte", "[shop]"),
    Hotel("Hotels & Übernachtung", "[tourism~\"^(hotel|motel|guest_house)$\"]"),
    Pharmacy("Apotheken", "[amenity=pharmacy]"),
    Hospital("Krankenhäuser", "[amenity=hospital]"),
    Police("Polizei", "[amenity=police]"),
    Workshop("Werkstätten", "[shop=car_repair]"),
    Bank("Banken & Geldautomaten", "[amenity~\"^(bank|atm)$\"]"),
    Transit("ÖPNV & Bahnhöfe", "[public_transport]"),
    Airport("Flughäfen", "[aeroway=aerodrome]"),
    Park("Parks & Freizeit", "[leisure]"),
}

internal data class PoiQueryResolution(
    val rawQuery: String,
    val category: PoiCategory?,
    val searchText: String,
    val suggestions: List<PoiCategory>,
    val needsUserConfirmation: Boolean,
)

internal object PoiQueryResolver {
    fun resolve(query: String): PoiQueryResolution {
        val normalized = query.trim().lowercase()
        require(normalized.length in 2..160) { "POI-Suchtext ist zu kurz oder zu lang." }
        val category = PoiCategory.entries.firstOrNull { candidate ->
            keywords(candidate).any { normalized.contains(it) }
        }
        if (category != null) {
            return PoiQueryResolution(query, category, query.trim(), emptyList(), false)
        }
        val suggestions = PoiCategory.entries
            .filter { candidate -> relatedKeywords(candidate).any { normalized.contains(it) } }
            .take(3)
        return PoiQueryResolution(
            rawQuery = query,
            category = null,
            searchText = query.trim(),
            suggestions = suggestions,
            needsUserConfirmation = true,
        )
    }

    private fun keywords(category: PoiCategory): List<String> = when (category) {
        PoiCategory.Fuel -> listOf("tankstelle", "tanken", "benzin", "diesel")
        PoiCategory.Charging -> listOf("ladestation", "ladesäule", "laden", "elektro")
        PoiCategory.Parking -> listOf("parkplatz", "parken", "parkhaus")
        PoiCategory.Restroom -> listOf("wc", "toilette", "toiletten")
        PoiCategory.RestArea -> listOf("raststätte", "raststaette", "rastplatz", "autohof")
        PoiCategory.Food -> listOf("restaurant", "essen", "café", "cafe", "fast food")
        PoiCategory.Shop -> listOf("supermarkt", "geschäft", "shop", "einkaufen")
        PoiCategory.Hotel -> listOf("hotel", "übernachtung", "motel")
        PoiCategory.Pharmacy -> listOf("apotheke")
        PoiCategory.Hospital -> listOf("krankenhaus", "klinik")
        PoiCategory.Police -> listOf("polizei")
        PoiCategory.Workshop -> listOf("werkstatt", "autoreparatur")
        PoiCategory.Bank -> listOf("bank", "geldautomat", "atm")
        PoiCategory.Transit -> listOf("bahnhof", "haltestelle", "bus")
        PoiCategory.Airport -> listOf("flughafen")
        PoiCategory.Park -> listOf("park", "freizeit", "spielplatz")
    }

    private fun relatedKeywords(category: PoiCategory): List<String> = when (category) {
        PoiCategory.Fuel -> listOf("auto", "fahrzeug")
        PoiCategory.Charging -> listOf("akku", "strom", "elektrisch")
        PoiCategory.Restroom, PoiCategory.RestArea -> listOf("pause", "rast")
        PoiCategory.Food -> listOf("catering", "mittag", "kaffee")
        PoiCategory.Shop -> listOf(" einkauf", "laden")
        else -> emptyList()
    }
}
