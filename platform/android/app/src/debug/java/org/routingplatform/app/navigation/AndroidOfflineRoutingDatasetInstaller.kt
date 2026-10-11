package org.routingplatform.app.navigation

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.config.ValhallaConfigFactory
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

data class OfflineRoutingImportProgress(
    val copiedBytes: Long,
    val totalBytes: Long?,
)

data class OfflineRoutingImportResult(
    val sizeBytes: Long,
    val sha256: String,
)

object AndroidOfflineRoutingDatasetInstaller {
    private const val MINIMUM_REMAINING_BYTES = 512L * 1024L * 1024L
    private const val PROBE_REQUEST =
        "{\"locations\":[{\"lat\":47.139651,\"lon\":9.521804}," +
            "{\"lat\":47.1412,\"lon\":9.5245}],\"costing\":\"auto\"," +
            "\"units\":\"kilometers\",\"language\":\"de-DE\"}"

    /**
     * Copies a selected Valhalla tile extract, checks the complete copy hash,
     * and runs a real local route in Vaduz before making it active.
     */
    fun install(
        context: Context,
        source: Uri,
        onProgress: (OfflineRoutingImportProgress) -> Unit = {},
    ): OfflineRoutingImportResult {
        val appContext = context.applicationContext
        val active = AndroidOfflineRoutingDatasetStore.tileArchive(appContext)
        val metadata = AndroidOfflineRoutingDatasetStore.metadataFile(appContext)
        val parent = active.parentFile ?: error("Offline tile directory is unavailable.")
        check(parent.exists() || parent.mkdirs()) { "Cannot create offline tile directory." }

        val temporary = File(parent, active.name + ".incoming")
        val metadataTemporary = File(parent, metadata.name + ".incoming")
        val backup = File(parent, active.name + ".backup")
        val metadataBackup = File(parent, metadata.name + ".backup")
        temporary.delete()
        metadataTemporary.delete()

        val totalBytes =
            appContext.contentResolver.openAssetFileDescriptor(source, "r")
                ?.use { it.length.takeIf { length -> length >= 0L } }

        val freeAtStart = StatFs(parent.absolutePath).availableBytes
        if (totalBytes != null) {
            require(freeAtStart - totalBytes >= MINIMUM_REMAINING_BYTES) {
                "Nicht genug freier Speicher: Für Import und Reserve werden mehr freie GB benötigt."
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        var copiedBytes = 0L
        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: error("Die ausgewählte Datei lässt sich nicht öffnen.")
            input.use { stream ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            throw InterruptedException("Import wurde abgebrochen.")
                        }
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(
                            freeAtStart - copiedBytes - count >= MINIMUM_REMAINING_BYTES
                        ) {
                            "Import angehalten: Es müssen mindestens 512 MB Speicherreserve frei bleiben."
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copiedBytes += count
                        onProgress(OfflineRoutingImportProgress(copiedBytes, totalBytes))
                    }
                    output.fd.sync()
                }
            }

            require(copiedBytes > 0L) { "Die ausgewählte Datei ist leer." }
            val sha256 = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            validateRoute(appContext, temporary)

            backup.delete()
            metadataBackup.delete()
            var previousArchiveMoved = false
            var previousMetadataMoved = false
            var newArchiveActivated = false
            try {
                if (active.exists()) {
                    check(active.renameTo(backup)) {
                        "Das bisherige Kartenpaket konnte nicht sicher gesichert werden."
                    }
                    previousArchiveMoved = true
                }
                if (metadata.exists()) {
                    check(metadata.renameTo(metadataBackup)) {
                        "Die bisherigen Integritätsdaten konnten nicht gesichert werden."
                    }
                    previousMetadataMoved = true
                }
                check(temporary.renameTo(active)) {
                    "Das geprüfte Kartenpaket konnte nicht aktiviert werden."
                }
                newArchiveActivated = true

                val properties = Properties().apply {
                    setProperty("schemaVersion", "1")
                    setProperty("sha256", sha256)
                    setProperty("sizeBytes", copiedBytes.toString())
                    setProperty("modifiedAt", active.lastModified().toString())
                    setProperty(
                        "valhallaVersion",
                        AndroidOfflineValhallaRouteSource.EXPECTED_VALHALLA_VERSION,
                    )
                }
                FileOutputStream(metadataTemporary).use { output ->
                    properties.store(output, "Validated offline routing dataset")
                    output.fd.sync()
                }
                check(metadataTemporary.renameTo(metadata)) {
                    "Die Integritätsdaten konnten nicht gespeichert werden."
                }

                backup.delete()
                metadataBackup.delete()
                return OfflineRoutingImportResult(copiedBytes, sha256)
            } catch (activationError: Throwable) {
                if (newArchiveActivated) active.delete()
                if (previousArchiveMoved) backup.renameTo(active)
                if (previousMetadataMoved) metadataBackup.renameTo(metadata)
                throw activationError
            }
        } catch (error: Throwable) {
            temporary.delete()
            metadataTemporary.delete()
            throw error
        }
    }

    private fun validateRoute(context: Context, archive: File) {
        Valhalla(
            context,
            ValhallaConfigFactory.usingTileExtract(archive.absolutePath),
        ).use { engine ->
            val result = JSONObject(engine.routeRaw(PROBE_REQUEST))
            val trip = result.getJSONObject("trip")
            require(trip.getJSONArray("legs").length() > 0) {
                "Die Datei enthält keine routbaren Valhalla-Kacheln."
            }
            require(trip.getJSONObject("summary").getDouble("length") > 0.0) {
                "Der lokale DACH-Routentest lieferte keine Strecke."
            }
        }
    }
}
