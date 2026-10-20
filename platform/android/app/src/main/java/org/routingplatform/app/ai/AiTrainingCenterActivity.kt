package org.routingplatform.app.ai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import org.routingplatform.app.ui.RoutingPlatformTheme
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

class AiTrainingCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoutingPlatformTheme {
                TrainingCenterScreen(
                    context = this,
                    onStart = { track, total, maxTokens, charging, focus ->
                        startSession(track, total, maxTokens, charging, focus)
                    },
                    onPause = { pauseSession() },
                    onResumeTraining = { resumeSession() },
                )
            }
        }
    }

    private fun startSession(track: AiTrainingTrack, total: Int, maxTokens: Int, charging: Boolean, focus: String) {
        val session = AiTrainingSession(
            id = UUID.randomUUID().toString(),
            trackId = track.id,
            total = total.coerceIn(5, 1200),
            completed = 0,
            passed = 0,
            averageLatencyMs = 0,
            status = "Wartet auf Bedingungen",
            requiresCharging = charging,
            maxTokens = maxTokens.coerceIn(32, 512),
            customFocus = focus.filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.take(50),
            startedAt = System.currentTimeMillis(),
            message = "Lokale Modellprüfung wird beim ersten Batch ausgeführt.",
        )
        AiTrainingStore.begin(this, session)
        AiTrainingScheduler.enqueue(this, session, append = false)
    }

    private fun pauseSession() {
        val session = AiTrainingStore.currentSession(this) ?: return
        WorkManager.getInstance(this).cancelUniqueWork(AiTrainingScheduler.workName(session.id))
        AiTrainingStore.update(this) { it.copy(status = "Pausiert", message = "Fortsetzbar; bisherige Messwerte bleiben lokal erhalten.") }
    }

    private fun resumeSession() {
        val session = AiTrainingStore.currentSession(this) ?: return
        if (session.completed >= session.total) return
        AiTrainingStore.update(this) { it.copy(status = "Wartet auf Bedingungen", message = "Lauf wird am nächsten offenen Beispiel fortgesetzt.") }
        val updated = AiTrainingStore.currentSession(this) ?: return
        AiTrainingScheduler.enqueue(this, updated, append = false)
    }
}

@androidx.compose.runtime.Composable
private fun TrainingCenterScreen(
    context: Context,
    onStart: (AiTrainingTrack, Int, Int, Boolean, String) -> Unit,
    onPause: () -> Unit,
    onResumeTraining: () -> Unit,
) {
    var session by remember { mutableStateOf(AiTrainingStore.currentSession(context)) }
    var results by remember { mutableStateOf(AiTrainingStore.results(context)) }
    var selectedTrack by remember { mutableStateOf(AiTrainingTrack.RouteIntent) }
    var exerciseCount by remember { mutableStateOf(24f) }
    var maxTokens by remember { mutableStateOf(128) }
    var chargingOnly by remember { mutableStateOf(true) }
    var autoStartOnOpen by remember { mutableStateOf(context.getSharedPreferences("ai_training_center_v1", Context.MODE_PRIVATE).getBoolean("auto_start", true)) }
    var focusPhrase by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        session = AiTrainingStore.currentSession(context)
        results = AiTrainingStore.results(context)
        if (autoStartOnOpen && session?.status !in setOf("Läuft", "Wartet auf Bedingungen", "Wartet auf Abkühlung", "Pausiert")) {
            val adaptiveTrack = AiTrainingPrioritizer.nextTrack(results)
            onStart(adaptiveTrack, 240, 128, true, "")
        }
        while (true) {
            session = AiTrainingStore.currentSession(context)
            results = AiTrainingStore.results(context)
            delay(1200)
        }
    }

    val trackResults = results.filter { it.trackId == selectedTrack.id }
    val lastResults = trackResults.takeLast(20)
    val passedCount = trackResults.count { it.passed }
    val passRate = if (trackResults.isEmpty()) 0 else (passedCount * 100 / trackResults.size)
    val averageLatency = trackResults.takeLast(30).map { it.latencyMs }.average().takeIf { it.isFinite() }?.roundToInt() ?: 0
    val isRunning = session?.status in setOf("Läuft", "Wartet auf Bedingungen", "Wartet auf Abkühlung", "Wartet auf freien Speicher")
    val canResume = session?.let { it.completed < it.total && (it.status == "Pausiert" || it.status == "Fehler") } == true
    val modelExists = File(context.filesDir, "g620-qwen2.5-1.5b-q4_k_m.gguf").isFile

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("KI-Trainingscenter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Lokale Übungsläufe · Auswertung · Wissenspflege", style = MaterialTheme.typography.bodySmall)
            }
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text("PIXEL · ON-DEVICE", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lokale KI-Laufzeit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusPill(if (modelExists) "Modell bereit" else "Modellprüfung nötig", modelExists)
                }
                Text("Antworten werden mit dem installierten Qwen-Modell direkt auf dem Handy erzeugt. Die Trainingsbatches benötigen kein Netzwerk.", style = MaterialTheme.typography.bodyMedium)
                Text("Wichtig: Diese Übungsläufe trainieren keine Modellgewichte. Sie messen Antworten; schwache und selten geprüfte Fälle werden bevorzugt. Jede Session kann am Ende einen KI-Testfall zur lokalen Prüfung vorschlagen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("Leistungsübersicht", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Beispiele", results.size.toString(), "${selectedTrack.title} · lokal", Modifier.weight(1f))
            MetricCard("Regelchecks", "$passRate %", "$passedCount bestanden", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("Ø Inferenz", if (averageLatency > 0) "$averageLatency ms" else "—", "${selectedTrack.title} · letzte 30", Modifier.weight(1f))
            MetricCard("Letzter Lauf", session?.status ?: "Noch keiner", session?.let { "${it.completed}/${it.total} Beispiele" } ?: "bereit", Modifier.weight(1f))
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Regelcheck-Verlauf", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Bestanden / nicht bestanden · letzte 20 Antworten", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TrainingTrendChart(lastResults.map { it.passed })
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Älter", style = MaterialTheme.typography.labelSmall)
                    Text("Neuester Test", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Trainingsprogramm", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Wähle einen Schwerpunkt. Jeder Lauf testet mehrere unterschiedliche Szenarien dieses Bereichs.", style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AiTrainingTrack.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { track ->
                                TrackCard(track, selectedTrack == track, Modifier.weight(1f)) { selectedTrack = track }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                Text("Umfang: ${exerciseCount.roundToInt()} lokale Tests", fontWeight = FontWeight.SemiBold)
                Slider(value = exerciseCount, onValueChange = { exerciseCount = it }, valueRange = 5f..1000f, steps = 19)
                Text("Antwortlänge: $maxTokens Tokens", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(64, 128, 256, 512).forEach { amount ->
                        FilterChip(selected = maxTokens == amount, onClick = { maxTokens = amount }, label = { Text("$amount") })
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = focusPhrase,
                    onValueChange = { focusPhrase = it.filter { ch -> ch.isLetterOrDigit() || ch == ' ' || ch == '-' }.take(50) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Eigener Wortschatz-Fokus") },
                    supportingText = { Text("Optional: ein Begriff oder kurzes Stichwort, keine Koordinaten oder Fahrdaten.") },
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Automatisch beim Öffnen starten", fontWeight = FontWeight.SemiBold)
                        Text("Startet einen adaptiven Lauf mit 240 Aufgaben. Jederzeit pausierbar.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(
                        checked = autoStartOnOpen,
                        onCheckedChange = {
                            autoStartOnOpen = it
                            context.getSharedPreferences("ai_training_center_v1", Context.MODE_PRIVATE).edit().putBoolean("auto_start", it).apply()
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Nur beim Laden", fontWeight = FontWeight.SemiBold)
                        Text("Zusätzlich Akku nicht niedrig; Arbeit kann bei gesperrtem Display warten.", style = MaterialTheme.typography.labelSmall)
                    }
                    Switch(checked = chargingOnly, onCheckedChange = { chargingOnly = it })
                }

                val current = session
                if (current != null && current.total > 0 && current.status in setOf("Läuft", "Wartet auf Bedingungen", "Pausiert", "Fehler")) {
                    val fraction = current.completed.toFloat() / current.total.toFloat()
                    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${current.status} · ${current.completed}/${current.total} · ${current.message}", style = MaterialTheme.typography.bodySmall)
                    if (current.completed > 0) {
                        Text("Regelchecks bestanden: ${current.passed}/${current.completed} · Ø ${current.averageLatencyMs} ms", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = { onStart(selectedTrack, exerciseCount.roundToInt(), maxTokens, chargingOnly, focusPhrase) },
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                ) { Text(if (isRunning) "Training läuft" else "Lokales Training starten") }

                if (isRunning) {
                    OutlinedButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) { Text("Pausieren") }
                } else if (canResume) {
                    OutlinedButton(onClick = onResumeTraining, modifier = Modifier.fillMaxWidth()) { Text("Lauf fortsetzen") }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Schwerpunkte & Qualität", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                AiTrainingTrack.entries.forEach { track ->
                    val rows = results.filter { it.trackId == track.id }.takeLast(30)
                    val rate = if (rows.isEmpty()) 0f else rows.count { it.passed }.toFloat() / rows.size
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(track.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { rate }, modifier = Modifier.weight(1.1f).height(8.dp))
                        Text(if (rows.isEmpty()) "—" else "${(rate * 100).roundToInt()}%", modifier = Modifier.size(width = 38.dp, height = 22.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text("Diese Werte sind regelbasierte Indikatoren, keine menschliche Qualitätsbewertung und kein Nachweis für sicherheitsreife Navigation.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Letzte Antworten", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                results.takeLast(8).asReversed().forEach { result ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(if (result.passed) "✓" else "•", color = if (result.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text("  ${AiTrainingTrack.fromId(result.trackId).title} · ${result.caseLabel}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${result.latencyMs} ms", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (results.isEmpty()) Text("Noch keine Läufe. Starte dein erstes lokales Übungsprogramm.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Echtes Modelltraining / LoRA", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Für ein Gewichts- oder LoRA-Update braucht die App einen mobilen Trainer, kompatible Trainingsgewichte und einen Adapter-Ladepfad. Der installierte Qwen-GGUF ist aktuell nur zur Inferenz eingebunden. Dieser Bereich zeigt deshalb transparent lokale Übungs- und Prüfläufe; er behauptet kein heimliches Modell-Update.", style = MaterialTheme.typography.bodySmall)
                Text("Offline · Messwerte bleiben auf dem Gerät · jederzeit pausierbar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun StatusPill(text: String, ready: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@androidx.compose.runtime.Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(detail, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@androidx.compose.runtime.Composable
private fun TrackCard(track: AiTrainingTrack, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(track.detail, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@androidx.compose.runtime.Composable
private fun TrainingTrendChart(values: List<Boolean>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.error
    Canvas(modifier = Modifier.fillMaxWidth().height(140.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp)) {
        val chartTop = 8f
        val chartBottom = size.height - 8f
        val left = 8f
        val right = size.width - 8f
        for (i in 0..4) {
            val y = chartTop + (chartBottom - chartTop) * i / 4f
            drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), 1f)
        }
        if (values.isEmpty()) return@Canvas
        val path = Path()
        values.forEachIndexed { index, passed ->
            val x = if (values.size == 1) (left + right) / 2f else left + (right - left) * index / (values.size - 1).toFloat()
            val y = if (passed) chartTop + 12f else chartBottom - 12f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(if (passed) line else muted, radius = 4.5f, center = androidx.compose.ui.geometry.Offset(x, y))
        }
        drawPath(path, line, style = Stroke(width = 3.5f))
        drawLine(lerp(muted, line, 0.5f), androidx.compose.ui.geometry.Offset(left, chartTop + 12f), androidx.compose.ui.geometry.Offset(right, chartTop + 12f), 1f)
    }
}
