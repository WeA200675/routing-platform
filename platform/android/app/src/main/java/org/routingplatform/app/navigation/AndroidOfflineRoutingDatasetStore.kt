package org.routingplatform.app.navigation

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.util.Properties

/**
 * App-private location and integrity metadata for the on-device DACH tiles.
 * Kept in the main source set so the app can report install status in every build.
 */
object AndroidOfflineRoutingDatasetStore {
    const val TILE_ARCHIVE_NAME = "valhalla-dach-merged.tar"
    const val EXPECTED_VALHALLA_VERSION = "3.9.1"

    private fun directory(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "routing-data").apply { if (!exists()) mkdirs() }
    }

    fun tileArchive(context: Context): File =
        File(directory(context), TILE_ARCHIVE_NAME)

    fun metadataFile(context: Context): File =
        File(directory(context), TILE_ARCHIVE_NAME + ".properties")

    fun isInstalled(context: Context): Boolean {
        val archive = tileArchive(context)
        val metadata = metadataFile(context)
        if (!archive.isFile || archive.length() <= 0L || !metadata.isFile) return false
        return runCatching {
            val values = Properties().apply {
                FileInputStream(metadata).use { load(it) }
            }
            values.getProperty("schemaVersion") == "1" &&
                values.getProperty("sizeBytes") == archive.length().toString() &&
                values.getProperty("modifiedAt") == archive.lastModified().toString() &&
                values.getProperty("sha256")?.matches(Regex("[a-f0-9]{64}")) == true &&
                values.getProperty("valhallaVersion") == EXPECTED_VALHALLA_VERSION
        }.getOrDefault(false)
    }

    fun installedSummary(context: Context): String {
        if (!isInstalled(context)) return "DACH-Kartenpaket nicht installiert"
        val archive = tileArchive(context)
        val sizeGiB = archive.length().toDouble() / (1024.0 * 1024.0 * 1024.0)
        return "DACH-Kartenpaket bereit · " + String.format(java.util.Locale.GERMANY, "%.1f GB", sizeGiB)
    }
}
