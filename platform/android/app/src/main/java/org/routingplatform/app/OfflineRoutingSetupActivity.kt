package org.routingplatform.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.navigation.AndroidOfflineRoutingDatasetInstaller
import org.routingplatform.app.navigation.AndroidOfflineRoutingDatasetStore
import org.routingplatform.app.navigation.OfflineRoutingImportProgress
import org.routingplatform.app.ui.RoutingPlatformTheme
import java.util.Locale
import java.util.concurrent.Executors

class OfflineRoutingSetupActivity : ComponentActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var installedSummary by remember {
                mutableStateOf(
                    AndroidOfflineRoutingDatasetStore.installedSummary(applicationContext)
                )
            }
            var status by remember { mutableStateOf("Bereit für den Kartenimport.") }
            var busy by remember { mutableStateOf(false) }
            var copiedBytes by remember { mutableLongStateOf(0L) }
            var totalBytes by remember { mutableStateOf<Long?>(null) }

            val picker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null && !busy) {
                    busy = true
                    status = "Karten werden sicher kopiert und lokal geprüft."
                    copiedBytes = 0L
                    totalBytes = null

                    worker.execute {
                        val result = runCatching {
                            AndroidOfflineRoutingDatasetInstaller.install(
                                context = applicationContext,
                                source = uri,
                                onProgress = { progress: OfflineRoutingImportProgress ->
                                    mainHandler.post {
                                        copiedBytes = progress.copiedBytes
                                        totalBytes = progress.totalBytes
                                    }
                                },
                            )
                        }
                        mainHandler.post {
                            busy = false
                            result.onSuccess {
                                installedSummary =
                                    AndroidOfflineRoutingDatasetStore
                                        .installedSummary(applicationContext)
                                status =
                                    "Import und lokale Vaduz-Routenprüfung erfolgreich. " +
                                        "Routing läuft jetzt direkt auf dem Handy."
                            }.onFailure { error ->
                                status =
                                    "Import nicht aktiviert: " +
                                        (error.message ?: "Datei konnte nicht geprüft werden.")
                            }
                        }
                    }
                }
            }

            RoutingPlatformTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = "Offline-Routing auf diesem Handy",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = installedSummary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text =
                                "Wähle das Valhalla-DACH-Kachelarchiv (.tar) aus den Dateien " +
                                    "des Pixel. Die App kopiert es in ihren geschützten Speicher, " +
                                    "prüft SHA-256 und testet eine echte Route bei Vaduz. " +
                                    "Erst nach erfolgreichem Test wird ein vorhandenes Paket ersetzt.",
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        if (busy) {
                            val total = totalBytes
                            if (total != null && total > 0L) {
                                LinearProgressIndicator(
                                    progress = {
                                        (copiedBytes.toFloat() / total.toFloat())
                                            .coerceIn(0f, 1f)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Text(
                                text = formatBytes(copiedBytes) +
                                    (total?.let { " von " + formatBytes(it) } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Text(text = status, style = MaterialTheme.typography.bodyMedium)

                        Button(
                            enabled = !busy,
                            onClick = {
                                picker.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (busy) "Import läuft" else "DACH-Kartenpaket auswählen")
                        }

                        Text(
                            text =
                                "Für den Import muss die Archivdatei bereits auf dem Pixel " +
                                    "verfügbar sein. Während der Fahrt braucht das lokale Routing " +
                                    "weder USB noch den PC-Routingdienst.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun formatBytes(value: Long): String =
        String.format(Locale.GERMANY, "%.2f GB", value.toDouble() / (1024.0 * 1024.0 * 1024.0))
}
