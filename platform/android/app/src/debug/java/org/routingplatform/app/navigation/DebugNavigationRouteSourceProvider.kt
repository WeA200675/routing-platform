package org.routingplatform.app.navigation

import android.content.Context

/**
 * Field-test source: route locally on the phone when DACH tiles are installed,
 * retaining the development HTTP service only as a failover.
 */
object DebugNavigationRouteSourceProvider {
    @JvmStatic
    fun create(context: Context, endpointText: String?): NavigationRouteSource =
        AndroidOfflineValhallaRouteSource.configured(context, endpointText)
}
