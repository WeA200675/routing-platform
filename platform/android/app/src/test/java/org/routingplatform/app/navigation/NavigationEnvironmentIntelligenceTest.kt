package org.routingplatform.app.navigation

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEnvironmentIntelligenceTest {

    @Test
    fun unknownObservedSatelliteIsDetected() {
        val known =
            satellite(
                svid =
                    11
            )

        val repository =
            NavigationSatelliteKnowledgeRepository(
                initialCatalog =
                    catalog(
                        version =
                            "v1",

                        generatedAt =
                            100L,

                        satellites =
                            listOf(
                                known
                            ),
                    )
            )

        val observed =
            listOf(
                observation(
                    svid =
                        11
                ),
                observation(
                    svid =
                        99
                ),
            )

        val unknown =
            unknownSatelliteKeys(
                observedSatellites =
                    observed,

                knowledge =
                    repository,
            )

        assertEquals(
            setOf(
                NavigationSatelliteKey(
                    constellation =
                        NavigationGnssConstellation.Galileo,

                    svid =
                        99,
                )
            ),
            unknown,
        )
    }

    @Test
    fun newerCatalogInstallsButOlderCatalogCannotRollback() {
        val repository =
            NavigationSatelliteKnowledgeRepository(
                initialCatalog =
                    catalog(
                        version =
                            "v1",

                        generatedAt =
                            100L,
                    )
            )

        val installed =
            repository.install(
                catalog(
                    version =
                        "v2",

                    generatedAt =
                        200L,
                )
            )

        assertEquals(
            NavigationSatelliteCatalogInstallStatus.Installed,
            installed.status,
        )

        val rollback =
            repository.install(
                catalog(
                    version =
                        "old",

                    generatedAt =
                        50L,
                )
            )

        assertEquals(
            NavigationSatelliteCatalogInstallStatus.RejectedOlder,
            rollback.status,
        )

        assertEquals(
            "v2",
            repository.catalogVersion(),
        )
    }

    @Test
    fun catalogRoundTripsThroughVersionedFileStore() {
        val directory =
            Files.createTempDirectory(
                "routing-satellite-catalog"
            ).toFile()

        try {
            val file =
                File(
                    directory,
                    "catalog.bin",
                )

            val store =
                FileNavigationSatelliteCatalogStore(
                    file
                )

            val original =
                catalog(
                    version =
                        "2026.09.05",

                    generatedAt =
                        123_456L,

                    satellites =
                        listOf(
                            satellite(
                                svid =
                                    23
                            )
                        ),
                )

            assertTrue(
                store.save(
                    original
                )
            )

            val restored =
                store.load()

            assertNotNull(
                restored
            )

            assertEquals(
                "2026.09.05",
                checkNotNull(
                    restored
                ).version,
            )

            assertEquals(
                1,
                restored
                    .satellites
                    .size,
            )

            assertEquals(
                23,
                restored
                    .satellites
                    .single()
                    .key
                    .svid,
            )

            assertEquals(
                1_575_420_000.0,
                restored
                    .satellites
                    .single()
                    .signals
                    .single()
                    .carrierFrequencyHz,
                0.1,
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun automaticControllerRequestsRefreshForUnknownSatellite() {
        val repository =
            NavigationSatelliteKnowledgeRepository(
                initialCatalog =
                    catalog(
                        version =
                            "v1",

                        generatedAt =
                            1_000L,

                        satellites =
                            listOf(
                                satellite(
                                    svid =
                                        1
                                )
                            ),
                    )
            )

        var requested:
            NavigationSatelliteCatalogRefreshRequest? =
            null

        val requestor =
            object :
                NavigationSatelliteCatalogRefreshRequestor {

                override fun requestRefresh(
                    request:
                        NavigationSatelliteCatalogRefreshRequest,
                ): Boolean {

                    requested =
                        request

                    return true
                }
            }

        val controller =
            NavigationSatelliteAutoUpdateController(
                knowledge =
                    repository,

                refreshRequestor =
                    requestor,

                maximumCatalogAgeMillis =
                    Long.MAX_VALUE,

                clockMillis = {
                    2_000L
                },
            )

        assertTrue(
            controller.observe(
                listOf(
                    observation(
                        svid =
                            77
                    )
                )
            )
        )

        assertEquals(
            NavigationSatelliteCatalogRefreshReason.UnknownSatellite,
            checkNotNull(
                requested
            ).reason,
        )

        assertEquals(
            77,
            requested
                ?.unknownSatellite
                ?.svid,
        )
    }

    @Test
    fun updateCoordinatorInstallsNormalizedRemoteCatalog() {
        val repository =
            NavigationSatelliteKnowledgeRepository(
                initialCatalog =
                    catalog(
                        version =
                            "v1",

                        generatedAt =
                            100L,
                    )
            )

        val source =
            object :
                NavigationSatelliteCatalogSource {

                override fun fetch(
                    currentVersion: String?,
                ): NavigationSatelliteCatalog? {

                    assertEquals(
                        "v1",
                        currentVersion,
                    )

                    return catalog(
                        version =
                            "v2",

                        generatedAt =
                            200L,
                    )
                }
            }

        val coordinator =
            NavigationSatelliteCatalogUpdateCoordinator(
                repository =
                    repository,

                source =
                    source,

                minimumRequestIntervalMillis =
                    0L,

                clockMillis = {
                    1_000L
                },
            )

        assertTrue(
            coordinator.requestRefresh(
                NavigationSatelliteCatalogRefreshRequest(
                    reason =
                        NavigationSatelliteCatalogRefreshReason.StaleCatalog,

                    unknownSatellite =
                        null,
                )
            )
        )

        assertEquals(
            "v2",
            repository.catalogVersion(),
        )
    }

    @Test
    fun privacyIdentifierIsStableInsideSessionButDoesNotExposeRawAddress() {
        val raw =
            "AA:BB:CC:DD:EE:FF"

        val firstHasher =
            NavigationPrivacyIdentifierHasher(
                ByteArray(
                    32
                ) {
                    1
                }
            )

        val secondHasher =
            NavigationPrivacyIdentifierHasher(
                ByteArray(
                    32
                ) {
                    2
                }
            )

        val first =
            firstHasher.token(
                namespace =
                    "wifi",

                rawIdentifier =
                    raw,
            )

        val repeated =
            firstHasher.token(
                namespace =
                    "wifi",

                rawIdentifier =
                    raw,
            )

        val otherSession =
            secondHasher.token(
                namespace =
                    "wifi",

                rawIdentifier =
                    raw,
            )

        assertEquals(
            first,
            repeated,
        )

        assertNotEquals(
            first,
            otherSession,
        )

        assertFalse(
            first.value.contains(
                "AA",
                ignoreCase =
                    true,
            ) &&
                first.value.contains(
                    "BB",
                    ignoreCase =
                        true,
                )
        )
    }

    @Test
    fun aiViewIsReadOnlyAndExposesUnknownCount() {
        val repository =
            NavigationSatelliteKnowledgeRepository(
                initialCatalog =
                    catalog(
                        version =
                            "v1",

                        generatedAt =
                            100L,

                        satellites =
                            listOf(
                                satellite(
                                    svid =
                                        1
                                )
                            ),
                    )
            )

        val unknownKey =
            NavigationSatelliteKey(
                constellation =
                    NavigationGnssConstellation.Galileo,

                svid =
                    44,
            )

        val snapshot =
            NavigationEnvironmentEvidenceSnapshot(
                observedSatellites =
                    emptyList(),

                radioObservations =
                    emptyList(),

                satelliteCatalogVersion =
                    "v1",

                unknownSatelliteKeys =
                    setOf(
                        unknownKey
                    ),

                capturedAtElapsedRealtimeNanos =
                    1L,
            )

        val ai =
            NavigationEnvironmentAiReadOnlyAdapter(
                snapshotProvider = {
                    snapshot
                },

                satelliteKnowledge =
                    repository,
            )

        assertEquals(
            "v1",
            ai
                .catalogStatus()
                .version,
        )

        assertEquals(
            1,
            ai
                .catalogStatus()
                .unknownSatelliteCount,
        )

        assertNull(
            ai.satelliteMetadata(
                unknownKey
            )
        )
    }

    @Test
    fun radioEvidenceSupportsRttDistanceWithoutRequiringRssi() {
        val observation =
            NavigationRadioObservation(
                technology =
                    NavigationRadioTechnology.WifiRtt,

                sourceId =
                    NavigationPrivacySafeRadioId(
                        "0123456789abcdef0123456789abcdef"
                    ),

                rssiDbm =
                    null,

                frequencyMhz =
                    null,

                distanceM =
                    12.4,

                distanceStdDevM =
                    0.8,

                elapsedRealtimeNanos =
                    100L,
            )

        assertEquals(
            12.4,
            checkNotNull(
                observation.distanceM
            ),
            0.001,
        )
    }

    private fun catalog(
        version: String,
        generatedAt: Long,
        satellites:
            List<NavigationSatelliteMetadata> =
            emptyList(),
    ) =
        NavigationSatelliteCatalog(
            version =
                version,

            generatedAtEpochMillis =
                generatedAt,

            satellites =
                satellites,
        )

    private fun satellite(
        svid: Int,
    ) =
        NavigationSatelliteMetadata(
            key =
                NavigationSatelliteKey(
                    constellation =
                        NavigationGnssConstellation.Galileo,

                    svid =
                        svid,
                ),

            spacecraftId =
                "GSAT-$svid",

            prn =
                "E$svid",

            operationalState =
                NavigationSatelliteOperationalState.Operational,

            signals =
                listOf(
                    NavigationSatelliteSignal(
                        carrierFrequencyHz =
                            1_575_420_000.0,

                        label =
                            "E1/L1",
                    )
                ),

            source =
                "test",

            sourceTimestampEpochMillis =
                100L,
        )

    private fun observation(
        svid: Int,
    ) =
        NavigationObservedSatellite(
            key =
                NavigationSatelliteKey(
                    constellation =
                        NavigationGnssConstellation.Galileo,

                    svid =
                        svid,
                ),

            cn0DbHz =
                35.0,

            azimuthDegrees =
                90.0,

            elevationDegrees =
                45.0,

            usedInFix =
                true,

            carrierFrequencyHz =
                1_575_420_000.0,

            hasAlmanacData =
                true,

            hasEphemerisData =
                true,

            elapsedRealtimeNanos =
                1_000L,
        )
}