package org.routingplatform.app.navigation

class JniNavigationCoreBridge :
    NavigationCoreBridge {

    init {
        System.loadLibrary(
            LIBRARY_NAME
        )
    }

    override fun currentSnapshot():
        NavigationUiSnapshot =
        enforceBoundary(
            nativeCurrentSnapshot()
                .toUiSnapshot()
        )

    override fun startNavigation():
        NavigationUiSnapshot =
        enforceBoundary(
            nativeStartNavigation()
                .toUiSnapshot()
        )

    private external fun nativeCurrentSnapshot():
        NativeNavigationSnapshot

    private external fun nativeStartNavigation():
        NativeNavigationSnapshot

    private fun enforceBoundary(
        snapshot: NavigationUiSnapshot,
    ): NavigationUiSnapshot {

        check(
            snapshot.presentationBoundaryIntact
        ) {
            "Native bridge crossed the Navigation Runtime presentation boundary."
        }

        return snapshot
    }

    companion object {
        const val LIBRARY_NAME =
            "routing_platform_jni"
    }
}
