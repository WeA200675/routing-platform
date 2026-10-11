package org.routingplatform.app.navigation

enum class NavigationGnssConstellation {
    Gps,
    Sbas,
    Glonass,
    Qzss,
    Beidou,
    Galileo,
    Irnss,
    Unknown,
}

data class NavigationSatelliteKey(
    val constellation: NavigationGnssConstellation,
    val svid: Int,
) {
    init {
        require(
            svid >= 0
        ) {
            "Satellite SVID must not be negative."
        }
    }
}

enum class NavigationSatelliteOperationalState {
    Operational,
    Test,
    Unhealthy,
    Outage,
    Retired,
    Unknown,
}

data class NavigationSatelliteSignal(
    val carrierFrequencyHz: Double,
    val label: String?,
) {
    init {
        require(
            carrierFrequencyHz.isFinite() &&
                carrierFrequencyHz > 0.0
        ) {
            "Carrier frequency must be finite and positive."
        }

        require(
            label == null ||
                label.isNotBlank()
        )
    }
}

data class NavigationSatelliteMetadata(
    val key: NavigationSatelliteKey,
    val spacecraftId: String?,
    val prn: String?,
    val operationalState:
        NavigationSatelliteOperationalState,
    val signals:
        List<NavigationSatelliteSignal>,
    val source: String,
    val sourceTimestampEpochMillis: Long?,
) {
    init {
        require(
            spacecraftId == null ||
                spacecraftId.isNotBlank()
        )

        require(
            prn == null ||
                prn.isNotBlank()
        )

        require(
            source.isNotBlank()
        )

        require(
            sourceTimestampEpochMillis == null ||
                sourceTimestampEpochMillis >= 0L
        )

        require(
            signals.distinct().size ==
                signals.size
        ) {
            "Satellite signals must not contain duplicates."
        }
    }
}

data class NavigationObservedSatellite(
    val key: NavigationSatelliteKey,
    val cn0DbHz: Double,
    val azimuthDegrees: Double,
    val elevationDegrees: Double,
    val usedInFix: Boolean,
    val carrierFrequencyHz: Double?,
    val hasAlmanacData: Boolean,
    val hasEphemerisData: Boolean,
    val elapsedRealtimeNanos: Long,
) {
    init {
        require(
            cn0DbHz.isFinite() &&
                cn0DbHz >= 0.0
        )

        require(
            azimuthDegrees.isFinite() &&
                azimuthDegrees >= 0.0 &&
                azimuthDegrees < 360.0
        )

        require(
            elevationDegrees.isFinite() &&
                elevationDegrees >= -90.0 &&
                elevationDegrees <= 90.0
        )

        require(
            carrierFrequencyHz == null ||
                (
                    carrierFrequencyHz.isFinite() &&
                        carrierFrequencyHz > 0.0
                )
        )

        require(
            elapsedRealtimeNanos >= 0L
        )
    }
}

class NavigationSatelliteCatalog(
    val version: String,
    val generatedAtEpochMillis: Long,
    satellites: List<NavigationSatelliteMetadata>,
) {
    val satellites:
        List<NavigationSatelliteMetadata> =
        satellites.toList()

    private val byKey =
        this.satellites.associateBy {
            it.key
        }

    init {
        require(
            version.isNotBlank()
        )

        require(
            generatedAtEpochMillis >= 0L
        )

        require(
            byKey.size ==
                this.satellites.size
        ) {
            "Satellite catalog contains duplicate keys."
        }
    }

    fun metadata(
        key: NavigationSatelliteKey,
    ): NavigationSatelliteMetadata? =
        byKey[key]

    companion object {
        fun empty() =
            NavigationSatelliteCatalog(
                version =
                    "empty",

                generatedAtEpochMillis =
                    0L,

                satellites =
                    emptyList(),
            )
    }
}

interface NavigationSatelliteKnowledgeView {
    fun catalog():
        NavigationSatelliteCatalog

    fun catalogVersion():
        String

    fun metadata(
        key: NavigationSatelliteKey,
    ): NavigationSatelliteMetadata?
}

interface NavigationSatelliteCatalogStore {
    fun load():
        NavigationSatelliteCatalog?

    fun save(
        catalog: NavigationSatelliteCatalog,
    ): Boolean
}

interface NavigationSatelliteCatalogSource {
    /*
     * Implementations provide a normalized, already validated
     * catalog from official GNSS data adapters.
     *
     * Returning null means "no newer catalog available".
     */
    fun fetch(
        currentVersion: String?,
    ): NavigationSatelliteCatalog?
}

enum class NavigationSatelliteCatalogInstallStatus {
    Installed,
    NoChange,
    RejectedOlder,
    StorageFailure,
}

data class NavigationSatelliteCatalogInstallResult(
    val status:
        NavigationSatelliteCatalogInstallStatus,

    val version:
        String,
)

class NavigationSatelliteKnowledgeRepository(
    initialCatalog:
        NavigationSatelliteCatalog =
        NavigationSatelliteCatalog.empty(),

    private val store:
        NavigationSatelliteCatalogStore? =
        null,
) :
    NavigationSatelliteKnowledgeView {

    @Volatile
    private var currentCatalog:
        NavigationSatelliteCatalog =
        chooseInitialCatalog(
            initialCatalog =
                initialCatalog,

            storedCatalog =
                runCatching {
                    store?.load()
                }.getOrNull(),
        )

    override fun catalog():
        NavigationSatelliteCatalog =
        currentCatalog

    override fun catalogVersion():
        String =
        currentCatalog.version

    override fun metadata(
        key: NavigationSatelliteKey,
    ): NavigationSatelliteMetadata? =
        currentCatalog.metadata(
            key
        )

    @Synchronized
    fun install(
        candidate: NavigationSatelliteCatalog,
    ): NavigationSatelliteCatalogInstallResult {

        val current =
            currentCatalog

        if (
            candidate.version ==
                current.version
        ) {
            return NavigationSatelliteCatalogInstallResult(
                status =
                    NavigationSatelliteCatalogInstallStatus.NoChange,

                version =
                    current.version,
            )
        }

        if (
            candidate.generatedAtEpochMillis <
                current.generatedAtEpochMillis
        ) {
            return NavigationSatelliteCatalogInstallResult(
                status =
                    NavigationSatelliteCatalogInstallStatus.RejectedOlder,

                version =
                    current.version,
            )
        }

        if (
            store != null &&
            !store.save(
                candidate
            )
        ) {
            return NavigationSatelliteCatalogInstallResult(
                status =
                    NavigationSatelliteCatalogInstallStatus.StorageFailure,

                version =
                    current.version,
            )
        }

        currentCatalog =
            candidate

        return NavigationSatelliteCatalogInstallResult(
            status =
                NavigationSatelliteCatalogInstallStatus.Installed,

            version =
                candidate.version,
        )
    }
}

enum class NavigationSatelliteCatalogRefreshReason {
    Startup,
    StaleCatalog,
    UnknownSatellite,
    Manual,
}

data class NavigationSatelliteCatalogRefreshRequest(
    val reason:
        NavigationSatelliteCatalogRefreshReason,

    val unknownSatellite:
        NavigationSatelliteKey?,
)

interface NavigationSatelliteCatalogRefreshRequestor {
    /*
     * This is deliberately NOT part of the AI read-only view.
     * The deterministic environment engine owns refresh execution.
     */
    fun requestRefresh(
        request:
            NavigationSatelliteCatalogRefreshRequest,
    ): Boolean
}

class NavigationSatelliteCatalogUpdateCoordinator(
    private val repository:
        NavigationSatelliteKnowledgeRepository,

    private val source:
        NavigationSatelliteCatalogSource,

    private val minimumRequestIntervalMillis:
        Long =
        DEFAULT_MINIMUM_REFRESH_INTERVAL_MILLIS,

    private val clockMillis:
        () -> Long = {
            System.currentTimeMillis()
        },

    private val dispatch:
        ((() -> Unit) -> Unit) = {
                task ->
            task()
        },
) :
    NavigationSatelliteCatalogRefreshRequestor {

    init {
        require(
            minimumRequestIntervalMillis >= 0L
        )
    }

    private var lastRequestMillis:
        Long? =
        null

    @Synchronized
    override fun requestRefresh(
        request:
            NavigationSatelliteCatalogRefreshRequest,
    ): Boolean {

        val now =
            clockMillis()

        val previous =
            lastRequestMillis

        if (
            previous != null &&
            now >= previous &&
            now - previous <
                minimumRequestIntervalMillis
        ) {
            return false
        }

        lastRequestMillis =
            now

        dispatch {
            val candidate =
                runCatching {
                    source.fetch(
                        repository.catalogVersion()
                    )
                }.getOrNull()

            if (candidate != null) {
                repository.install(
                    candidate
                )
            }
        }

        return true
    }
}

class NavigationSatelliteAutoUpdateController(
    private val knowledge:
        NavigationSatelliteKnowledgeView,

    private val refreshRequestor:
        NavigationSatelliteCatalogRefreshRequestor,

    private val maximumCatalogAgeMillis:
        Long =
        DEFAULT_MAXIMUM_CATALOG_AGE_MILLIS,

    private val clockMillis:
        () -> Long = {
            System.currentTimeMillis()
        },
) {
    init {
        require(
            maximumCatalogAgeMillis >
                0L
        )
    }

    fun observe(
        satellites:
            List<NavigationObservedSatellite>,
    ): Boolean {

        val catalog =
            knowledge.catalog()

        if (
            catalog.version ==
                "empty"
        ) {
            return refreshRequestor.requestRefresh(
                NavigationSatelliteCatalogRefreshRequest(
                    reason =
                        NavigationSatelliteCatalogRefreshReason.Startup,

                    unknownSatellite =
                        null,
                )
            )
        }

        val unknown =
            unknownSatelliteKeys(
                observedSatellites =
                    satellites,

                knowledge =
                    knowledge,
            )

        if (
            unknown.isNotEmpty()
        ) {
            return refreshRequestor.requestRefresh(
                NavigationSatelliteCatalogRefreshRequest(
                    reason =
                        NavigationSatelliteCatalogRefreshReason.UnknownSatellite,

                    unknownSatellite =
                        unknown.first(),
                )
            )
        }

        val now =
            clockMillis()

        if (
            now >=
                catalog.generatedAtEpochMillis &&
            now -
                catalog.generatedAtEpochMillis >=
                    maximumCatalogAgeMillis
        ) {
            return refreshRequestor.requestRefresh(
                NavigationSatelliteCatalogRefreshRequest(
                    reason =
                        NavigationSatelliteCatalogRefreshReason.StaleCatalog,

                    unknownSatellite =
                        null,
                )
            )
        }

        return false
    }
}

fun unknownSatelliteKeys(
    observedSatellites:
        List<NavigationObservedSatellite>,

    knowledge:
        NavigationSatelliteKnowledgeView,
): Set<NavigationSatelliteKey> =
    observedSatellites
        .asSequence()
        .map {
            it.key
        }
        .filter {
            knowledge.metadata(
                it
            ) ==
                null
        }
        .toSet()

private fun chooseInitialCatalog(
    initialCatalog:
        NavigationSatelliteCatalog,

    storedCatalog:
        NavigationSatelliteCatalog?,
): NavigationSatelliteCatalog {

    if (
        storedCatalog == null
    ) {
        return initialCatalog
    }

    return if (
        storedCatalog.generatedAtEpochMillis >=
            initialCatalog.generatedAtEpochMillis
    ) {
        storedCatalog
    } else {
        initialCatalog
    }
}

private const val DEFAULT_MINIMUM_REFRESH_INTERVAL_MILLIS =
    6L *
        60L *
        60L *
        1000L

private const val DEFAULT_MAXIMUM_CATALOG_AGE_MILLIS =
    24L *
        60L *
        60L *
        1000L