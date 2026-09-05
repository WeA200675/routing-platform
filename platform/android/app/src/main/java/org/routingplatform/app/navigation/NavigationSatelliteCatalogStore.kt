package org.routingplatform.app.navigation

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileNavigationSatelliteCatalogStore(
    private val file: File,
) :
    NavigationSatelliteCatalogStore {

    private val temporaryFile =
        File(
            file.path +
                ".tmp"
        )

    private val backupFile =
        File(
            file.path +
                ".bak"
        )

    override fun load():
        NavigationSatelliteCatalog? {

        val primary =
            readCatalog(
                file
            )

        if (
            primary != null
        ) {
            return primary
        }

        return readCatalog(
            backupFile
        )
    }

    override fun save(
        catalog: NavigationSatelliteCatalog,
    ): Boolean {

        return runCatching {
            file.parentFile
                ?.mkdirs()

            if (
                temporaryFile.exists()
            ) {
                check(
                    temporaryFile.delete()
                )
            }

            writeCatalog(
                temporaryFile,
                catalog,
            )

            if (
                backupFile.exists()
            ) {
                check(
                    backupFile.delete()
                )
            }

            if (
                file.exists()
            ) {
                check(
                    file.renameTo(
                        backupFile
                    )
                )
            }

            if (
                !temporaryFile.renameTo(
                    file
                )
            ) {
                if (
                    backupFile.exists()
                ) {
                    backupFile.renameTo(
                        file
                    )
                }

                error(
                    "Unable to atomically install satellite catalog."
                )
            }

            if (
                backupFile.exists()
            ) {
                backupFile.delete()
            }

            true
        }.getOrElse {
            temporaryFile.delete()

            if (
                !file.exists() &&
                backupFile.exists()
            ) {
                backupFile.renameTo(
                    file
                )
            }

            false
        }
    }

    private fun writeCatalog(
        destination: File,
        catalog: NavigationSatelliteCatalog,
    ) {
        val fileOutput =
            FileOutputStream(
                destination
            )

        try {
            DataOutputStream(
                BufferedOutputStream(
                    fileOutput
                )
            ).use {
                    output ->

                output.writeInt(
                    FILE_MAGIC
                )

                output.writeInt(
                    FILE_SCHEMA_VERSION
                )

                output.writeUTF(
                    catalog.version
                )

                output.writeLong(
                    catalog.generatedAtEpochMillis
                )

                output.writeInt(
                    catalog.satellites.size
                )

                catalog.satellites.forEach {
                        satellite ->

                    output.writeUTF(
                        satellite
                            .key
                            .constellation
                            .name
                    )

                    output.writeInt(
                        satellite
                            .key
                            .svid
                    )

                    output.writeNullableString(
                        satellite.spacecraftId
                    )

                    output.writeNullableString(
                        satellite.prn
                    )

                    output.writeUTF(
                        satellite
                            .operationalState
                            .name
                    )

                    output.writeUTF(
                        satellite.source
                    )

                    output.writeNullableLong(
                        satellite
                            .sourceTimestampEpochMillis
                    )

                    output.writeInt(
                        satellite.signals.size
                    )

                    satellite.signals.forEach {
                            signal ->

                        output.writeDouble(
                            signal.carrierFrequencyHz
                        )

                        output.writeNullableString(
                            signal.label
                        )
                    }
                }

                output.flush()

                fileOutput
                    .fd
                    .sync()
            }
        } finally {
            runCatching {
                fileOutput.close()
            }
        }
    }

    private fun readCatalog(
        source: File,
    ): NavigationSatelliteCatalog? {

        if (
            !source.isFile
        ) {
            return null
        }

        return runCatching {
            DataInputStream(
                BufferedInputStream(
                    FileInputStream(
                        source
                    )
                )
            ).use {
                    input ->

                require(
                    input.readInt() ==
                        FILE_MAGIC
                )

                require(
                    input.readInt() ==
                        FILE_SCHEMA_VERSION
                )

                val version =
                    input.readUTF()

                val generatedAt =
                    input.readLong()

                val satelliteCount =
                    input.readInt()

                require(
                    satelliteCount in
                        0..MAX_SATELLITE_COUNT
                )

                val satellites =
                    ArrayList<
                        NavigationSatelliteMetadata
                    >(
                        satelliteCount
                    )

                repeat(
                    satelliteCount
                ) {
                    val constellationName =
                        input.readUTF()

                    val constellation =
                        NavigationGnssConstellation
                            .values()
                            .firstOrNull {
                                it.name ==
                                    constellationName
                            }
                            ?: NavigationGnssConstellation.Unknown

                    val svid =
                        input.readInt()

                    val spacecraftId =
                        input.readNullableString()

                    val prn =
                        input.readNullableString()

                    val stateName =
                        input.readUTF()

                    val state =
                        NavigationSatelliteOperationalState
                            .values()
                            .firstOrNull {
                                it.name ==
                                    stateName
                            }
                            ?: NavigationSatelliteOperationalState.Unknown

                    val metadataSource =
                        input.readUTF()

                    val sourceTimestamp =
                        input.readNullableLong()

                    val signalCount =
                        input.readInt()

                    require(
                        signalCount in
                            0..MAX_SIGNALS_PER_SATELLITE
                    )

                    val signals =
                        ArrayList<
                            NavigationSatelliteSignal
                        >(
                            signalCount
                        )

                    repeat(
                        signalCount
                    ) {
                        signals.add(
                            NavigationSatelliteSignal(
                                carrierFrequencyHz =
                                    input.readDouble(),

                                label =
                                    input.readNullableString(),
                            )
                        )
                    }

                    satellites.add(
                        NavigationSatelliteMetadata(
                            key =
                                NavigationSatelliteKey(
                                    constellation =
                                        constellation,

                                    svid =
                                        svid,
                                ),

                            spacecraftId =
                                spacecraftId,

                            prn =
                                prn,

                            operationalState =
                                state,

                            signals =
                                signals,

                            source =
                                metadataSource,

                            sourceTimestampEpochMillis =
                                sourceTimestamp,
                        )
                    )
                }

                NavigationSatelliteCatalog(
                    version =
                        version,

                    generatedAtEpochMillis =
                        generatedAt,

                    satellites =
                        satellites,
                )
            }
        }.getOrNull()
    }
}

private fun DataOutputStream.writeNullableString(
    value: String?,
) {
    writeBoolean(
        value != null
    )

    if (
        value != null
    ) {
        writeUTF(
            value
        )
    }
}

private fun DataInputStream.readNullableString():
    String? =
    if (
        readBoolean()
    ) {
        readUTF()
    } else {
        null
    }

private fun DataOutputStream.writeNullableLong(
    value: Long?,
) {
    writeBoolean(
        value != null
    )

    if (
        value != null
    ) {
        writeLong(
            value
        )
    }
}

private fun DataInputStream.readNullableLong():
    Long? =
    if (
        readBoolean()
    ) {
        readLong()
    } else {
        null
    }

private const val FILE_MAGIC =
    0x52505343

private const val FILE_SCHEMA_VERSION =
    1

private const val MAX_SATELLITE_COUNT =
    100_000

private const val MAX_SIGNALS_PER_SATELLITE =
    64