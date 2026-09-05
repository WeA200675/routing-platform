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

        return enforceBoundary(
            nativeUpdateProgress(
                shapeSegmentIndex,
                segmentFraction,
            ).toUiSnapshot()
        )
    }

    private external fun nativeCurrentSnapshot():
        NativeNavigationSnapshot

    private external fun nativeStartNavigation():
        NativeNavigationSnapshot

    private external fun nativeUpdateProgress(
        shapeSegmentIndex: Int,
        segmentFraction: Double,
    ): NativeNavigationSnapshot

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
