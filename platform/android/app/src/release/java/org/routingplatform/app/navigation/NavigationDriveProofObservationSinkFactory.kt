package org.routingplatform.app.navigation

import android.content.Context

/*
 * Release/production factory.
 *
 * The bounded recorder implementation exists only in src/debug.
 */
internal object NavigationDriveProofObservationSinkFactory {

    @Suppress("UNUSED_PARAMETER")
    fun create(
        context:
            Context,
    ): NavigationDriveProofObservationSink =
        NoOpNavigationDriveProofObservationSink
}
