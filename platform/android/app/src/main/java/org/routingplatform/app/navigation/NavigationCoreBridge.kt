package org.routingplatform.app.navigation

interface NavigationCoreBridge {

    fun currentSnapshot():
        NavigationUiSnapshot

    fun startNavigation():
        NavigationUiSnapshot

    fun stopNavigation():
        NavigationUiSnapshot

    fun updateProgress(
        shapeSegmentIndex: Int,
        segmentFraction: Double,
    ): NavigationUiSnapshot
}
