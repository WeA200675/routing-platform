package org.routingplatform.app.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import org.routingplatform.app.navigation.RoutePoint

class AndroidGeocoderDestinationSearchSource(
    context:
        Context,
) :
    DestinationSearchSource {

    private val appContext =
        context.applicationContext

    private val geocoder =
        Geocoder(
            appContext,
            Locale.getDefault(),
        )

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val executor =
        Executors
            .newSingleThreadExecutor()

    private val closed =
        AtomicBoolean(
            false
        )

    override fun search(
        query:
            String,

        onResult:
            (
                Result<
                    List<DestinationSearchResult>
                >
            ) -> Unit,
    ): DestinationSearchHandle {

        check(
            !closed.get()
        ) {
            "Destination search source is closed."
        }

        val normalized =
            query.trim()

        require(
            normalized.length in
                MIN_SEARCH_QUERY_LENGTH..
                    MAX_SEARCH_QUERY_LENGTH
        ) {
            "Destination search query length is invalid."
        }

        if (
            !Geocoder.isPresent()
        ) {
            mainHandler.post {
                if (
                    !closed.get()
                ) {
                    onResult(
                        Result.failure(
                            IllegalStateException(
                                "Android Geocoder ist auf diesem Gerät nicht verfügbar."
                            )
                        )
                    )
                }
            }

            return object :
                DestinationSearchHandle {

                override fun cancel() =
                    Unit
            }
        }

        val cancelled =
            AtomicBoolean(
                false
            )

        val future:
            Future<*> =
            executor.submit {
                val result =
                    runCatching {
                        lookup(
                            normalized
                        )
                    }

                mainHandler.post {
                    if (
                        !cancelled.get() &&
                        !closed.get()
                    ) {
                        onResult(
                            result
                        )
                    }
                }
            }

        return object :
            DestinationSearchHandle {

            override fun cancel() {
                cancelled.set(
                    true
                )

                future.cancel(
                    true
                )
            }
        }
    }

    override fun close() {
        if (
            closed.compareAndSet(
                false,
                true,
            )
        ) {
            executor.shutdownNow()
        }
    }

    @Suppress("DEPRECATION")
    private fun lookup(
        query:
            String,
    ): List<DestinationSearchResult> {

        val addresses =
            geocoder
                .getFromLocationName(
                    query,
                    MAX_DESTINATION_SEARCH_RESULTS,
                )
                ?: emptyList()

        val distinct =
            addresses
                .filter {
                    it.hasLatitude() &&
                        it.hasLongitude() &&
                        it.latitude.isFinite() &&
                        it.latitude in
                            -90.0..90.0 &&
                        it.longitude.isFinite() &&
                        it.longitude in
                            -180.0..180.0
                }
                .distinctBy {
                    Pair(
                        (it.latitude * 1_000_000.0)
                            .toLong(),

                        (it.longitude * 1_000_000.0)
                            .toLong(),
                    )
                }
                .take(
                    MAX_DESTINATION_SEARCH_RESULTS
                )

        return distinct
            .mapIndexed {
                    index,
                    address ->

                val point =
                    RoutePoint(
                        latitude =
                            address.latitude,

                        longitude =
                            address.longitude,
                    )

                val primary =
                    primaryLabel(
                        address =
                            address,

                        fallback =
                            query,
                    )

                val secondary =
                    address
                        .getAddressLine(
                            0
                        )
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty() &&
                                it !=
                                primary
                        }

                DestinationSearchResult(
                    id =
                        "geocoder-$index-" +
                            point.latitude +
                            "-" +
                            point.longitude,

                    primaryLabel =
                        primary,

                    secondaryLabel =
                        secondary,

                    point =
                        point,
                )
            }
    }

    private fun primaryLabel(
        address:
            Address,

        fallback:
            String,
    ): String =
        sequenceOf(
            address.featureName,
            address.thoroughfare,
            address.locality,
            address.subAdminArea,
            address.adminArea,
            address.countryName,
            address.getAddressLine(
                0
            ),
            fallback,
        )
            .mapNotNull {
                it
                    ?.trim()
                    ?.takeIf {
                            value ->

                        value.isNotEmpty()
                    }
            }
            .first()
}

private const val MIN_SEARCH_QUERY_LENGTH =
    2

private const val MAX_SEARCH_QUERY_LENGTH =
    160
