package org.routingplatform.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.ai.SocialAiProductRuntime
import org.routingplatform.app.ai.SocialAiRuntimeInput
import org.routingplatform.app.ai.SocialAiPersonalitySettings
import org.routingplatform.app.ai.SocialAiResponseContext
import org.routingplatform.app.ui.RoutingPlatformTheme
import java.util.concurrent.Executors

class LocalAiActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var runtime: SocialAiProductRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val localRuntime = SocialAiProductRuntime(applicationContext)
        runtime = localRuntime
        setContent {
            RoutingPlatformTheme {
                var status by remember { mutableStateOf("Modell noch nicht installiert. Der einmalige Download ist etwa 940 MiB.") }
                var busy by remember { mutableStateOf(false) }
                var progress by remember { mutableStateOf(0f) }
                var input by remember { mutableStateOf("") }
                val messages = remember { mutableStateListOf<Pair<String, String>>() }
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Lokale Navigations-KI", style = MaterialTheme.typography.headlineSmall)
                        Text("Qwen2.5 1.5B läuft auf diesem Gerät. Gespräch und Modell bleiben lokal.", style = MaterialTheme.typography.bodyMedium)
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        if (busy && progress > 0f) {
                            androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        if (!busy) {
                            Button(onClick = {
                                busy = true
                                status = "Prüfe Modell und native Laufzeit …"
                                executor.execute {
                                    val state = localRuntime.setupState()
                                    if (state == org.routingplatform.app.ai.SocialAiSetupState.ModelMissing) {
                                        runOnUiThread { status = "Lade das gepinnte Modell herunter … WLAN wird empfohlen." }
                                        val result = localRuntime.installModel { received, total ->
                                            runOnUiThread { progress = if (total > 0) received.toFloat() / total else 0f }
                                        }
                                        runOnUiThread {
                                            status = when (result) {
                                                is org.routingplatform.app.ai.SocialAiModelInstallResult.Installed -> "Modell installiert und SHA-256 geprüft. Du kannst jetzt schreiben."
                                                is org.routingplatform.app.ai.SocialAiModelInstallResult.Rejected -> "Installation fehlgeschlagen: ${result.reason}. Navigation und Standardhinweise funktionieren weiter."
                                            }
                                            busy = false
                                        }
                                    } else {
                                        runOnUiThread {
                                            status = when (state) {
                                                org.routingplatform.app.ai.SocialAiSetupState.Ready -> "Modell und native Laufzeit sind bereit."
                                                org.routingplatform.app.ai.SocialAiSetupState.ModelMissing -> "Modell fehlt."
                                                org.routingplatform.app.ai.SocialAiSetupState.NativeRuntimeMissing -> "Diese APK enthält die optionale native KI-Laufzeit nicht. Standardnavigation bleibt verfügbar."
                                                is org.routingplatform.app.ai.SocialAiSetupState.Unavailable -> state.reason
                                            }
                                            busy = false
                                        }
                                    }
                                }
                            }) { Text("Modell prüfen / installieren") }
                        }
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(messages) { (role, message) -> Text("$role: $message", style = MaterialTheme.typography.bodyMedium) }
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it.take(2000) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nachricht") },
                            enabled = !busy,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy && input.isNotBlank(), onClick = {
                                val prompt = input.trim()
                                input = ""
                                messages.add("Du" to prompt)
                                busy = true
                                status = "Die lokale KI antwortet …"
                                executor.execute {
                                    val answer = localRuntime.generate(
                                        input = SocialAiRuntimeInput(
                                            settings = SocialAiPersonalitySettings(),
                                            context = SocialAiResponseContext.Conversation,
                                        ),
                                        text = prompt,
                                    )?.text
                                    runOnUiThread {
                                        if (answer.isNullOrBlank()) {
                                            status = "Lokale KI nicht verfügbar. Standardnavigation und deterministische Hinweise bleiben verfügbar."
                                        } else {
                                            messages.add("KI" to answer)
                                            status = "Antwort lokal erzeugt."
                                        }
                                        busy = false
                                    }
                                }
                            }) { Text("Senden") }
                            Button(onClick = { finish() }, enabled = !busy) { Text("Zur Navigation") }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        runtime?.close()
        runtime = null
        executor.shutdownNow()
        super.onDestroy()
    }
}
