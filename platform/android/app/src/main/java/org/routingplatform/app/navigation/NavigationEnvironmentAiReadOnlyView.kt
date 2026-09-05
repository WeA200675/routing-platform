package org.routingplatform.app.navigation

data class NavigationEnvironmentAiCatalogStatus(
    val version: String,
    val generatedAtEpochMillis: Long,
    val unknownSatelliteCount: Int,
)

interface NavigationEnvironmentAiReadOnlyView {
    /*
     * No setters, no permission controls, no radio controls,
     * no catalog installation and no JNI access.
     */
    fun environmentSnapshot():
        NavigationEnvironmentEvidenceSnapshot

    fun satelliteMetadata(
        key: NavigationSatelliteKey,
    ): NavigationSatelliteMetadata?

    fun catalogStatus():
        NavigationEnvironmentAiCatalogStatus
}

class NavigationEnvironmentAiReadOnlyAdapter(
    private val snapshotProvider:
        () -> NavigationEnvironmentEvidenceSnapshot,

    private val satelliteKnowledge:
        NavigationSatelliteKnowledgeView,
) :
    NavigationEnvironmentAiReadOnlyView {

    override fun environmentSnapshot():
        NavigationEnvironmentEvidenceSnapshot =
        snapshotProvider()

    override fun satelliteMetadata(
        key: NavigationSatelliteKey,
    ): NavigationSatelliteMetadata? =
        satelliteKnowledge.metadata(
            key
        )

    override fun catalogStatus():
        NavigationEnvironmentAiCatalogStatus {

        val snapshot =
            snapshotProvider()

        val catalog =
            satelliteKnowledge.catalog()

        return NavigationEnvironmentAiCatalogStatus(
            version =
                catalog.version,

            generatedAtEpochMillis =
                catalog.generatedAtEpochMillis,

            unknownSatelliteCount =
                snapshot
                    .unknownSatelliteKeys
                    .size,
        )
    }
}