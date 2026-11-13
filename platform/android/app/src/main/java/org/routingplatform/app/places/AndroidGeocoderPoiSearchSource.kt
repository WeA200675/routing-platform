package org.routingplatform.app.places

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/** Offline-capable POI adapter. It uses the platform geocoder and normalizes results
 * into the same contract used by network providers. A cache can be layered above it. */
class AndroidGeocoderPoiSearchSource(
    context: Context,
) : PoiSearchSource {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    override fun search(request: PoiSearchRequest): List<NavigationPoi> {
        val query = request.query?.trim().orEmpty()
        if (query.isBlank()) return emptyList()
        val addresses = runCatching {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, request.maxResults.coerceIn(1, 20))
        }.getOrDefault(emptyList())
        val category = request.category ?: PoiCategory.REST_AREA
        val now = System.currentTimeMillis()
        return addresses.mapIndexed { index, address ->
            NavigationPoi(
                id = "geocoder:${address.latitude}:${address.longitude}:$index",
                name = address.featureName?.takeIf { it.isNotBlank() } ?: query,
                category = category,
                latitude = address.latitude,
                longitude = address.longitude,
                distanceMeters = 0.0,
                detourSeconds = null,
                openNow = null,
                capturedAtEpochMs = now,
                source = "android-geocoder",
            )
        }
    }
}


