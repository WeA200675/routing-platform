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
            NavigationRouteFamily.ProfileFastest,
            NavigationRouteFamily.ProfileShortest,
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
        return ParallelNavigationRouteSource(
            delegateFactory = { family ->
                AndroidHttpNavigationRouteSource(
                    context = context,
                    endpoint = uri,
                )
            },
            families = families,
        )
    }
}
