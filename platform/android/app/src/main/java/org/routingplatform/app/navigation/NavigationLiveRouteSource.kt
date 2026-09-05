package org.routingplatform.app.navigation

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean


data class NavigationRouteRequest(
    val origin: RoutePoint,
    val destination: RoutePoint,

    val viaPoints:
        List<RoutePoint> =
        emptyList(),

    val family:
        NavigationRouteFamily =
        NavigationRouteFamily.ProfileOptimal,
) {
    init {
        require(
            origin.latitude.isFinite() &&
                origin.latitude in -90.0..90.0
        )

        require(
            origin.longitude.isFinite() &&
                origin.longitude in -180.0..180.0
        )

        require(
            destination.latitude.isFinite() &&
                destination.latitude in -90.0..90.0
        )

        require(
            destination.longitude.isFinite() &&
                destination.longitude in -180.0..180.0
        )

        require(
            viaPoints.size <=
                MAX_ROUTE_VIA_POINTS
        ) {
            "Too many navigation via points."
        }

        viaPoints.forEach {
            require(
                it.latitude.isFinite() &&
                    it.latitude in -90.0..90.0
            )

            require(
                it.longitude.isFinite() &&
                    it.longitude in -180.0..180.0
            )
        }
    }
}

interface NavigationRouteAcquisitionHandle {
    fun cancel()
}

interface NavigationRouteSource :
    AutoCloseable {

    fun acquire(
        request: NavigationRouteRequest,
        onResult:
            (
                Result<NavigationRouteContract>
            ) -> Unit,
    ): NavigationRouteAcquisitionHandle

    override fun close()
}

class AndroidHttpNavigationRouteSource(
    context: Context,
    private val endpoint: URI,
) : NavigationRouteSource {

    private val appContext =
        context.applicationContext

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val executor =
        Executors.newSingleThreadExecutor()

    private val closed =
        AtomicBoolean(
            false
        )

    private val debuggable =
        (
            appContext.applicationInfo.flags and
                ApplicationInfo.FLAG_DEBUGGABLE
        ) !=
            0

    init {
        validateEndpoint(
            endpoint =
                endpoint,

            debuggable =
                debuggable,
        )
    }

    override fun acquire(
        request: NavigationRouteRequest,
        onResult:
            (
                Result<NavigationRouteContract>
            ) -> Unit,
    ): NavigationRouteAcquisitionHandle {

        check(
            !closed.get()
        ) {
            "Navigation route source is closed."
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
                        fetch(
                            request
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
            NavigationRouteAcquisitionHandle {

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

    private fun fetch(
        request:
            NavigationRouteRequest,
    ): NavigationRouteContract {

        val connection =
            endpoint
                .toURL()
                .openConnection()
                as? HttpURLConnection
                ?: error(
                    "Navigation route endpoint is not HTTP(S)."
                )

        try {
            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                CONNECT_TIMEOUT_MS

            connection.readTimeout =
                READ_TIMEOUT_MS

            connection.instanceFollowRedirects =
                false

            connection.doOutput =
                true

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8",
            )

            connection.setRequestProperty(
                "Accept",
                "application/json",
            )

            if (
                isAllowedDevelopmentLoopback(
                    endpoint
                )
            ) {
                connection.setRequestProperty(
                    DEVELOPMENT_HEADER,
                    "1",
                )
            }

            val body =
                NavigationRouteRequestJson
                    .encode(
                        request
                    )
                    .toByteArray(
                        Charsets.UTF_8
                    )

            require(
                body.size <=
                    MAX_REQUEST_BYTES
            ) {
                "Navigation route request is too large."
            }

            connection.outputStream.use {
                    output ->

                output.write(
                    body
                )
            }

            val responseCode =
                connection.responseCode

            val responseBody =
                readBoundedBody(
                    connection =
                        connection,

                    successful =
                        responseCode in
                            200..299,
                )

            if (
                responseCode !in
                    200..299
            ) {
                throw IllegalStateException(
                    "Navigation route service returned HTTP " +
                        responseCode +
                        ": " +
                        responseBody.take(
                            500
                        )
                )
            }

            val route =
                AndroidNavigationRouteContractJson
                    .parse(
                        responseBody
                    )

            check(
                route.engineName.isNotBlank()
            ) {
                "Live route response has no routing engine identity."
            }

            check(
                route.geometry.size >=
                    2
            )

            return route
        } finally {
            connection.disconnect()
        }
    }
}

object NavigationRouteSourceFactory {
    const val ENDPOINT_METADATA_KEY =
        "org.routingplatform.navigation.ROUTE_ENDPOINT"

    fun fromManifest(
        context: Context,
    ): NavigationRouteSource? {

        val applicationInfo =
            context.packageManager
                .getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )

        val endpointText =
            applicationInfo
                .metaData
                ?.getString(
                    ENDPOINT_METADATA_KEY
                )
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?: return null

        return AndroidHttpNavigationRouteSource(
            context =
                context,

            endpoint =
                URI(
                    endpointText
                ),
        )
    }
}

internal object NavigationRouteRequestJson {
    /*
     * Pure Kotlin serializer.
     *
     * NavigationRouteRequest already validates every coordinate as
     * finite, so Double.toString() always yields valid JSON numeric
     * syntax for admitted coordinates.
     *
     * The family value comes exclusively from our enum mapping.
     */
    fun encode(
        request: NavigationRouteRequest,
    ): String =
        buildString {
            append(
                "{\"origin\":"
            )

            appendPoint(
                request.origin
            )

            append(
                ",\"destination\":"
            )

            appendPoint(
                request.destination
            )

            append(
                ",\"viaPoints\":["
            )

            request.viaPoints
                .forEachIndexed {
                        index,
                        point ->

                    if (
                        index > 0
                    ) {
                        append(
                            ','
                        )
                    }

                    appendPoint(
                        point
                    )
                }

            append(
                "],\"family\":\""
            )

            append(
                familyKey(
                    request.family
                )
            )

            append(
                "\"}"
            )
        }

    private fun StringBuilder.appendPoint(
        point:
            RoutePoint,
    ) {
        append(
            "{\"latitude\":"
        )

        append(
            point.latitude
        )

        append(
            ",\"longitude\":"
        )

        append(
            point.longitude
        )

        append(
            '}'
        )
    }
}
private fun familyKey(
    family:
        NavigationRouteFamily,
): String =
    when (
        family
    ) {
        NavigationRouteFamily.Fastest ->
            "fastest"

        NavigationRouteFamily.Shortest ->
            "shortest"

        NavigationRouteFamily.ProfileOptimal ->
            "profile_optimal"

        NavigationRouteFamily.MajorRoads ->
            "major_roads"

        NavigationRouteFamily.Comfort ->
            "comfort"

        NavigationRouteFamily.LowUrban ->
            "low_urban"

        NavigationRouteFamily.LowCurvature ->
            "low_curvature"

        NavigationRouteFamily.LowGradient ->
            "low_gradient"

        NavigationRouteFamily.LowTraffic ->
            "low_traffic"

        NavigationRouteFamily.Energy ->
            "energy"

        NavigationRouteFamily.Scenic ->
            "scenic"

        NavigationRouteFamily.Stable ->
            "stable"
    }

private fun validateEndpoint(
    endpoint: URI,
    debuggable: Boolean,
) {
    val scheme =
        endpoint.scheme
            ?.lowercase()

    if (
        scheme ==
            "https"
    ) {
        return
    }

    if (
        scheme ==
            "http" &&
        debuggable &&
        isAllowedDevelopmentLoopback(
            endpoint
        )
    ) {
        return
    }

    throw IllegalArgumentException(
        "Production navigation route endpoints must use HTTPS; " +
            "debug HTTP is allowed only on loopback."
    )
}

private fun isAllowedDevelopmentLoopback(
    endpoint: URI,
): Boolean {

    val host =
        endpoint.host
            ?.lowercase()
            ?: return false

    return host ==
        "127.0.0.1" ||
        host ==
            "localhost" ||
        host ==
            "::1"
}

private fun readBoundedBody(
    connection:
        HttpURLConnection,

    successful:
        Boolean,
): String {

    val declaredLength =
        connection.contentLengthLong

    if (
        declaredLength >
            MAX_RESPONSE_BYTES
    ) {
        throw IllegalStateException(
            "Navigation route response exceeds size limit."
        )
    }

    val stream =
        if (
            successful
        ) {
            connection.inputStream
        } else {
            connection.errorStream
        }
            ?: return ""

    return stream.use {
            input ->

        val output =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                8192
            )

        var total =
            0

        while (true) {
            val read =
                input.read(
                    buffer
                )

            if (
                read <
                    0
            ) {
                break
            }

            total +=
                read

            if (
                total >
                    MAX_RESPONSE_BYTES
            ) {
                throw IllegalStateException(
                    "Navigation route response exceeds size limit."
                )
            }

            output.write(
                buffer,
                0,
                read,
            )
        }

        String(
            output.toByteArray(),
            Charsets.UTF_8,
        )
    }
}

private const val DEVELOPMENT_HEADER =
    "X-Routing-Platform-Dev"

private const val CONNECT_TIMEOUT_MS =
    5_000

private const val READ_TIMEOUT_MS =
    30_000

private const val MAX_REQUEST_BYTES =
    64 * 1024

private const val MAX_RESPONSE_BYTES =
    16 * 1024 * 1024

private const val MAX_ROUTE_VIA_POINTS =
    16