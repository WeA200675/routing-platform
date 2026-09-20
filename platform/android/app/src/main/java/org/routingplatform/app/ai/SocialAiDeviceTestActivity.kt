package org.routingplatform.app.ai

import android.app.ActivityManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.routingplatform.app.ui.RoutingPlatformTheme
import java.io.File
import java.security.MessageDigest

class SocialAiDeviceTestActivity : ComponentActivity() {
    @Volatile private var status by mutableStateOf("Bereit. Der Test läuft vollständig lokal auf dem Gerät.")
    @Volatile private var running by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoutingPlatformTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    ) {
                        Text("G6.20 Local AI Device Test", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Prüft Modell-Hash, native Runtime, Modell-Laden und echte lokale Textgenerierung.")
                        Spacer(Modifier.height(20.dp))
                        Button(
                            enabled = !running,
                            onClick = { runDeviceTest() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (running) "Test läuft …" else "Local AI Test starten")
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(status)
                    }
                }
            }
        }
    }

    private fun runDeviceTest() {
        if (running) return
        running = true
        status = "1/4 Modell wird aus dem signierten RC vorbereitet …"
        Thread {
            val result = runCatching {
                val started = SystemClock.elapsedRealtime()
                val model = materializeVerifiedModel()
                update("2/4 Modell verifiziert. Native Runtime wird geladen …")
                val engine = JniSocialAiNativeEngine()
                val memory = ActivityManager.MemoryInfo().also {
                    (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
                }
                update("3/4 Runtime geladen. Modell wird initialisiert …\nVerfügbarer RAM: ${memory.availMem / (1024 * 1024)} MiB")
                check(engine.loadModel(model.absolutePath, 2048)) { "Native Runtime hat das Modell abgelehnt." }
                try {
                    update("4/4 Modell geladen. Lokale Inferenz läuft …")
                    val inferenceStart = SystemClock.elapsedRealtime()
                    val answer = engine.generate(
                        "You are a concise offline navigation assistant. Answer in German.\n\nUSER:\nSag in einem kurzen Satz, dass die lokale KI auf diesem Pixel funktioniert.\nASSISTANT:\n",
                        48
                    ).trim()
                    val inferenceMs = SystemClock.elapsedRealtime() - inferenceStart
                    check(answer.isNotBlank()) { "Die Runtime lieferte eine leere Antwort." }
                    val totalMs = SystemClock.elapsedRealtime() - started
                    "PASS ✓\n\nLokale Antwort:\n$answer\n\nInferenz: ${inferenceMs} ms\nGesamttest: ${totalMs} ms\nNetzwerk-Fallback: keiner"
                } finally {
                    engine.unloadModel()
                }
            }.getOrElse { error ->
                "FAIL ✗\n\n${error.javaClass.simpleName}: ${error.message ?: "Unbekannter Fehler"}"
            }
            runOnUiThread {
                status = result
                running = false
            }
        }.start()
    }

    private fun update(text: String) = runOnUiThread { status = text }

    private fun materializeVerifiedModel(): File {
        val target = File(filesDir, MODEL_FILE)
        if (!target.isFile || sha256(target) != MODEL_SHA256) {
            val tmp = File(filesDir, "$MODEL_FILE.tmp")
            assets.open(MODEL_ASSET).use { input ->
                tmp.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            check(sha256(tmp) == MODEL_SHA256) { "Modell-SHA-256 stimmt nicht mit candidate.lock überein." }
            if (target.exists()) check(target.delete())
            check(tmp.renameTo(target)) { "Verifiziertes Modell konnte nicht aktiviert werden." }
        }
        check(sha256(target) == MODEL_SHA256) { "Persistiertes Modell ist nicht mehr unverändert." }
        return target
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MODEL_ASSET = "g620/SmolLM2-360M-Instruct-Q4_K_M.gguf"
        private const val MODEL_FILE = "g620-smollm2-360m-q4_k_m.gguf"
        private const val MODEL_SHA256 = "16c7f1667fea34bacad196a57b548effcb37614db4ab5677a20c8c7b823b9e63"
    }
}
