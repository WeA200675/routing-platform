package org.routingplatform.app.navigation

class JniNavigationCoreBridge :
    NavigationCoreBridge {

    @Volatile
    private var installedRoute:
        NavigationRouteContract? =
        null

    init {
        System.loadLibrary(
            LIBRARY_NAME
        )
    }

    override fun currentSnapshot():
        NavigationUiSnapshot =
        finalizeNativeSnapshot(
            nativeCurrentSnapshot()
                .toUiSnapshot()
        )

    override fun startNavigation():
        NavigationUiSnapshot =
        finalizeNativeSnapshot(
            nativeStartNavigation()
                .toUiSnapshot()
        )

    override fun stopNavigation():
        NavigationUiSnapshot =
        finalizeNativeSnapshot(
            nativeStopNavigation()
                .toUiSnapshot()
        )

    override fun updateProgress(
        shapeSegmentIndex: Int,
        segmentFraction: Double,
    ): NavigationUiSnapshot {

        require(shapeSegmentIndex >= 0) {
            "shapeSegmentIndex must not be negative."
        }

        require(segmentFraction in 0.0..1.0) {
            "segmentFraction must be in [0, 1]."
        }

        return finalizeNativeSnapshot(
            nativeUpdateProgress(
                shapeSegmentIndex,
                segmentFraction,
            ).toUiSnapshot()
        )
    }

    fun installRoute(
        route:
            NavigationRouteContract,
    ): NavigationUiSnapshot {

        val payload =
            NavigationRouteNativeCodec
                .encode(
                    route
                )

        val nativeSnapshot =
            enforceBoundary(
                nativeInstallRoute(
                    payload
                ).toUiSnapshot()
            )

        requireNativeBoundary(
            condition =
                nativeSnapshot.routeId ==
                    route.routeId,

            detail =
                "Native route installation returned another route id.",
        )

        requireNativeBoundary(
            condition =
                nativeSnapshot.geometry.size ==
                    route.geometry.size,

            detail =
                "Native route installation changed route geometry size.",
        )

        /*
         * Only publish Kotlin-side route metadata after native
         * installation has completed successfully.
         */
        installedRoute =
            route

        return decorateInstalledRoute(
            snapshot =
                nativeSnapshot,

            route =
                route,
        )
    }

    fun replaceNavigatingRoute(
        route:
            NavigationRouteContract,
    ): NavigationUiSnapshot {

        val previous =
            currentSnapshot()

        requireNativeBoundary(
            condition =
                previous.state ==
                    NavigationSessionState.Navigating,

            detail =
                "Active route replacement requires Navigating state.",
        )

        val payload =
            NavigationRouteNativeCodec
                .encode(
                    route
                )

        val nativeSnapshot =
            enforceBoundary(
                nativeReplaceNavigatingRoute(
                    payload
                ).toUiSnapshot()
            )

        requireNativeBoundary(
            condition =
                nativeSnapshot.state ==
                    NavigationSessionState.Navigating,

            detail =
                "Replacement route did not start a new navigation session.",
        )

        requireNativeBoundary(
            condition =
                nativeSnapshot.sessionId !=
                    previous.sessionId,

            detail =
                "Replacement route did not create a new native session.",
        )

        requireNativeBoundary(
            condition =
                nativeSnapshot.routeId ==
                    route.routeId,

            detail =
                "Replacement route returned another route id.",
        )

        requireNativeBoundary(
            condition =
                nativeSnapshot.geometry.size ==
                    route.geometry.size,

            detail =
                "Replacement route changed geometry size.",
        )

        installedRoute =
            route

        return decorateInstalledRoute(
            snapshot =
                nativeSnapshot,

            route =
                route,
        )
    }

    private external fun nativeCurrentSnapshot():
        NativeNavigationSnapshot

    private external fun nativeStartNavigation():
        NativeNavigationSnapshot

    private external fun nativeStopNavigation():
        NativeNavigationSnapshot

    private external fun nativeUpdateProgress(
        shapeSegmentIndex: Int,
        segmentFraction: Double,
    ): NativeNavigationSnapshot

    private external fun nativeInstallRoute(
        routePayload: ByteArray,
    ): NativeNavigationSnapshot

    private external fun nativeReplaceNavigatingRoute(
        routePayload: ByteArray,
    ): NativeNavigationSnapshot

    private fun finalizeNativeSnapshot(
        snapshot: NavigationUiSnapshot,
    ): NavigationUiSnapshot {

        val safeSnapshot =
            enforceBoundary(
                snapshot
            )

        val route =
            installedRoute
                ?: return safeSnapshot

        return decorateInstalledRoute(
            snapshot =
                safeSnapshot,

            route =
                route,
        )
    }

    private fun decorateInstalledRoute(
        snapshot:
            NavigationUiSnapshot,

        route:
            NavigationRouteContract,
    ): NavigationUiSnapshot {

        if (
            snapshot.routeId !=
                route.routeId
        ) {
            return snapshot
        }

        val uiManeuvers =
            route.maneuvers.map {
                it.toNavigationManeuver()
            }

        val maneuverIndex =
            currentManeuverIndex(
                snapshot =
                    snapshot,

                route =
                    route,
            )

        val current =
            maneuverIndex
                ?.let {
                    uiManeuvers[
                        it
                    ]
                }
                ?: snapshot.currentManeuver

        val next =
            maneuverIndex
                ?.let {
                    index ->

                    uiManeuvers
                        .getOrNull(
                            index +
                                1
                        )
                }
                ?: snapshot.nextManeuver

        return snapshot.copy(
            currentManeuver =
                current,

            nextManeuver =
                next,

            routeManeuvers =
                uiManeuvers,

            engineName =
                route.engineName,

            engineVersion =
                route.engineVersion,

            segmentDataStatus =
                route.segmentDataStatus,

            routeDiagnostics =
                route.diagnostics,
        )
    }

    private fun currentManeuverIndex(
        snapshot:
            NavigationUiSnapshot,

        route:
            NavigationRouteContract,
    ): Int? {

        if (
            route.maneuvers.isEmpty()
        ) {
            return null
        }

        if (
            snapshot.state ==
                NavigationSessionState.Arrived
        ) {
            return route.maneuvers.lastIndex
        }

        val shapePosition =
            snapshot
                .shapeSegmentIndex
                .toDouble() +
                snapshot
                    .segmentFraction

        val index =
            route.maneuvers
                .indexOfFirst {
                    shapePosition <=
                        it.endShapeIndex
                            .toDouble()
                }

        return if (
            index >=
                0
        ) {
            index
        } else {
            route.maneuvers.lastIndex
        }
    }

    private fun enforceBoundary(
        snapshot: NavigationUiSnapshot,
    ): NavigationUiSnapshot {

        requireNativeBoundary(
            condition =
                snapshot.presentationBoundaryIntact,

            detail =
                "Native bridge crossed the Navigation Runtime presentation boundary.",
        )

        return snapshot
    }

    companion object {
        const val LIBRARY_NAME =
            "routing_platform_jni"
    }
}