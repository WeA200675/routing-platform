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
        PoiCategory.Fuel -> listOf("tankstelle", "tanken", "benzin", "diesel", "fuel", "gas station", "petrol", "gasolinera", "station service", "stazione di servizio")
        PoiCategory.Charging -> listOf("ladestation", "ladesäule", "laden", "elektro", "charging station", "ev charger", "borne de recharge", "colonnina", "cargador")
        PoiCategory.Parking -> listOf("parkplatz", "parken", "parkhaus", "parking", "car park", "parking garage", "parking voiture", "parcheggio", "aparcamiento")
        PoiCategory.Restroom -> listOf("wc", "toilette", "toiletten", "toilet", "restroom", "bathroom", "sanitaires", "toilette", "baño", "bagno")
        PoiCategory.RestArea -> listOf("raststätte", "raststaette", "rastplatz", "autohof", "rest area", "service area", "aire de repos", "area di servizio", "área de servicio")
        PoiCategory.Food -> listOf("restaurant", "essen", "café", "cafe", "fast food", "food", "dining", "restaurant", "comida", "ristorante")
        PoiCategory.Shop -> listOf("supermarkt", "geschäft", "shop", "einkaufen", "supermarket", "grocery", "store", "magasin", "supermercado", "negozio")
        PoiCategory.Hotel -> listOf("hotel", "übernachtung", "motel", "lodging", "accommodation", "hôtel", "alojamiento", "alloggio")
        PoiCategory.Pharmacy -> listOf("apotheke", "pharmacy", "pharmacie", "farmacia")
        PoiCategory.Hospital -> listOf("krankenhaus", "klinik", "hospital", "clinic", "hôpital", "clinica", "hospital")
        PoiCategory.Police -> listOf("polizei", "police", "policía", "polizia")
        PoiCategory.Workshop -> listOf("werkstatt", "autoreparatur", "car repair", "garage", "atelier", "taller", "officina")
        PoiCategory.Bank -> listOf("bank", "geldautomat", "atm", "cash machine", "banque", "bankomat", "banco", "bancomat")
        PoiCategory.Transit -> listOf("bahnhof", "haltestelle", "bus", "train station", "station", "bus stop", "gare", "stazione", "estación")
        PoiCategory.Airport -> listOf("flughafen", "airport", "aéroport", "aeroporto", "aeropuerto")
        PoiCategory.Park -> listOf("park", "freizeit", "spielplatz", "park", "leisure", "playground", "parc", "parco", "parque")
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
