package org.routingplatform.app.navigation

import android.content.Context
import android.content.pm.PackageManager
import java.net.URI

/**
 * Production factory for multi-family routing. Each family gets its own
 * cancellable HTTP source, so Valhalla requests are truly concurrent.
 */
object ParallelNavigationRouteSourceFactory {
    fun fromManifest(
        context: Context,
        families: List<NavigationRouteFamily> = listOf(
            NavigationRouteFamily.ProfileOptimal,
            NavigationRouteFamily.Fastest,
            NavigationRouteFamily.Shortest,
        ),
    ): NavigationRouteSource? {
        val info = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )
        val endpoint = info.metaData
            ?.getString(NavigationRouteSourceFactory.ENDPOINT_METADATA_KEY)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val uri = URI(endpoint)
        val liveProviders = NavigationLiveProviderFactory.fromManifest(context)
        return ParallelNavigationRouteSource(
            delegateFactory = { family ->
                AndroidHttpNavigationRouteSource(
                    context = context,
                    endpoint = uri,
                )
            },
            liveEventsProvider = {
                val now = System.currentTimeMillis()
                liveProviders.flatMap { provider ->
                    runCatching { provider.fetch(now) }.getOrDefault(emptyList())
                }
            },
            families = families,
        )
    }
}
