package org.routingplatform.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.routingplatform.app.navigation.AndroidOfflineRoutingDatasetStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/** Local SAF importer for the offline routing archive. No storage permission is required. */
class OfflineRoutingSetupActivity : ComponentActivity() {
    private lateinit var status: TextView

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        status.text = "Kartenpaket wird geprüft und kopiert …"
        val result = runCatching { importArchive(uri) }
        status.text = result.fold(
            onSuccess = { AndroidOfflineRoutingDatasetStore.installedSummary(applicationContext) },
            onFailure = { "Import fehlgeschlagen: ${it.message ?: "unbekannter Fehler"}" },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { setPadding(32, 32, 32, 16) }
        val choose = Button(this).apply {
            text = "Offline-Kartenpaket auswählen"
            setOnClickListener { picker.launch(arrayOf("application/octet-stream", "application/x-tar", "*/*")) }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            addView(status)
            addView(choose)
        }
        setContentView(layout)
        status.text = AndroidOfflineRoutingDatasetStore.installedSummary(applicationContext)
    }

    private fun importArchive(uri: android.net.Uri) {
        val resolver = contentResolver
        val expectedName = AndroidOfflineRoutingDatasetStore.TILE_ARCHIVE_NAME
        val destination = AndroidOfflineRoutingDatasetStore.tileArchive(this)
        val staged = File(destination.parentFile, "$expectedName.importing")
        val available = resolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
        require(available != 0L) { "Die ausgewählte Datei ist leer." }
        require((destination.parentFile?.usableSpace ?: 0L) > MIN_FREE_BYTES) { "Nicht genügend Speicherplatz für den Kartenimport." }
        runCatching { staged.delete() }
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(staged).use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
        } ?: error("Die ausgewählte Datei konnte nicht gelesen werden.")
        require(staged.length() > 0L) { "Die ausgewählte Datei ist leer." }
        val digest = sha256(staged)
        require(staged.renameTo(destination)) { "Kartenpaket konnte nicht sicher installiert werden." }
        val properties = Properties().apply {
            put("schemaVersion", "1")
            put("sizeBytes", destination.length().toString())
            put("modifiedAt", destination.lastModified().toString())
            put("sha256", digest)
            put("valhallaVersion", AndroidOfflineRoutingDatasetStore.EXPECTED_VALHALLA_VERSION)
        }
        FileOutputStream(AndroidOfflineRoutingDatasetStore.metadataFile(this)).use { properties.store(it, "Routing Platform offline dataset") }
        require(AndroidOfflineRoutingDatasetStore.isInstalled(this)) { "Kartenpaket-Integrität konnte nicht bestätigt werden." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 1024 * 1024
        const val MIN_FREE_BYTES = 512L * 1024 * 1024
    }
}
