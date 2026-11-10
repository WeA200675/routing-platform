package org.routingplatform.app.navigation

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes actual network availability and triggers an immediate upload retry
 * after a connection becomes available.
 */
class NavigationNetworkRecoveryMonitor(
    context: Context,
    private val onAvailable: () -> Unit,
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val available = AtomicBoolean(false)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (available.getAndSet(true).not()) onAvailable()
        }

        override fun onLost(network: Network) {
            available.set(false)
        }
    }

    fun start() {
        available.set(connectivity.activeNetwork != null)
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
        available.set(false)
    }
}
