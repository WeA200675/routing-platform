package org.routingplatform.app.ai

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal enum class AiTrainingTrack(val id: String, val title: String, val detail: String) {
    Vocabulary("vocabulary", "Wortschatz", "Begriffe klar und natürlich erklären"),
    Voice("voice", "Ansagen", "Kurze, eindeutige Sprachführung"),
    RouteIntent("route_intent", "Routenwünsche", "Ziel, Vermeidung und Zwischenstopp erkennen"),
    Maneuvers("maneuvers", "Abbiegen & Ausfahrten", "Richtungsangaben verständlich formulieren"),
    GpsSafety("gps_safety", "GPS & Sicherheit", "Einzelfehler nicht als bestätigte Abweichung behandeln"),
    Traffic("traffic", "Stau & Gefahren", "Fehlende Live-Daten offen benennen");

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: RouteIntent
    }
}

internal data class TrainingCase(
    val prompt: String,
    val requiredTerms: List<String>,
    val label: String,
)

internal val trainingCases = mapOf(
    AiTrainingTrack.Vocabulary to listOf(
        TrainingCase("Erkläre den Begriff Zwischenziel in einem kurzen, natürlichen Satz und nenne ausdrücklich das Wort Zwischenstopp.", listOf("zwischenziel", "zwischenstopp"), "Zwischenziel"),
        TrainingCase("Erkläre knapp den Unterschied zwischen Reststrecke und Restzeit. Verwende beide Begriffe.", listOf("reststrecke", "restzeit"), "Navigationsbegriffe"),
        TrainingCase("Formuliere verständlich, dass eine Route neu berechnet wird, wenn ein Fahrer bewusst von ihr abweicht. Nutze das Wort Umweg.", listOf("route", "umweg"), "Routenwortschatz"),
        TrainingCase("Erkläre einem Fahranfänger kurz, was eine Ausfahrt ist. Verwende das Wort Autobahn.", listOf("ausfahrt", "autobahn"), "Ausfahrt"),
    ),
    AiTrainingTrack.Voice to listOf(
        TrainingCase("Gib eine kurze Sprachansage. Der Fahrer soll in 300 Metern rechts abbiegen. Nenne Entfernung und Richtung.", listOf("300", "rechts"), "Ansage kurz"),
        TrainingCase("Formuliere diese Ansage freundlich und eindeutig: Im Kreisverkehr die zweite Ausfahrt nehmen.", listOf("kreisverkehr", "zweite", "ausfahrt"), "Kreisverkehr"),
        TrainingCase("Fasse in einem Satz zusammen: Noch 2 Kilometer bis zum Ziel, voraussichtliche Ankunft in 4 Minuten.", listOf("2", "kilometer", "4", "minuten"), "Entfernung & Zeit"),
        TrainingCase("Gib nur eine klare Ansage: Auf der Autobahn rechts halten und der Ausfahrt Richtung Nürnberg folgen.", listOf("autobahn", "rechts", "ausfahrt"), "Autobahnausfahrt"),
    ),
    AiTrainingTrack.RouteIntent to listOf(
        TrainingCase("Fasse den Auftrag zusammen: Ziel Zuhause, Autobahnen vermeiden, vorher an einem Supermarkt halten.", listOf("zuhause", "autobahn", "supermarkt"), "Mehrteiliger Auftrag"),
        TrainingCase("Der Fahrer möchte zum Bahnhof, Mautstraßen vermeiden und vorher tanken. Nenne alle drei Wünsche.", listOf("bahnhof", "maut", "tanken"), "Vermeidung & Stopp"),
        TrainingCase("Das Ziel ist unklar. Stelle genau eine Rückfrage und starte keine Route.", listOf("ziel", "?"), "Unklares Ziel"),
        TrainingCase("Der Nutzer sagt: 'Fahr mich zum See, aber nicht über die Autobahn.' Fasse Ziel und Vermeidung knapp zusammen.", listOf("see", "autobahn"), "Ziel & Vermeidung"),
    ),
    AiTrainingTrack.Maneuvers to listOf(
        TrainingCase("Gib eine eindeutige Ansage: In 500 Metern rechts auf die Ausfahrt A92 Richtung Deggendorf fahren.", listOf("500", "rechts", "ausfahrt", "a92"), "Autobahnabfahrt"),
        TrainingCase("Die Fahrspurinformation ist nicht verfügbar. Erfinde keine Spur und sage, dass die Beschilderung maßgeblich ist.", listOf("spur", "beschilderung"), "Spursicherheit"),
        TrainingCase("Die Route sagt links abbiegen, die Karte zeigt aber keine passende Kreuzung. Weise auf die Unsicherheit hin und fordere zum Folgen der Beschilderung auf.", listOf("unsicher", "beschilderung"), "Widerspruch"),
        TrainingCase("Formuliere: Dem Straßenverlauf 1 Kilometer geradeaus folgen, danach links abbiegen.", listOf("1", "kilometer", "geradeaus", "links"), "Folgemanöver"),
    ),
    AiTrainingTrack.GpsSafety to listOf(
        TrainingCase("Ein einzelner GPS-Punkt liegt neben der Straße. Bestätige keinen Abweichungsalarm und warte auf einen zweiten plausiblen Messpunkt.", listOf("einzel", "zweiten", "messpunkt"), "Einzel-Ausreißer"),
        TrainingCase("Die Positionsgenauigkeit ist niedrig. Erkläre, dass die Routenposition zunächst gehalten wird und keine sichere Neuberechnung vorliegt.", listOf("genauigkeit", "gehalten", "neuberechnung"), "Niedrige Genauigkeit"),
        TrainingCase("Der Kompass springt einmal um 90 Grad. Weise darauf hin, dass eine einzelne Messung keine sichere Fahrtrichtung bestätigt.", listOf("einzel", "messung", "richtung"), "Heading-Ausreißer"),
        TrainingCase("Es liegen zwei widersprüchliche Positionsmessungen vor. Erfinde keine Abbiegeanweisung; bitte um Orientierung an der Beschilderung.", listOf("widersprüch", "beschilderung"), "Widersprüchliche Messwerte"),
    ),
    AiTrainingTrack.Traffic to listOf(
        TrainingCase("Es liegen keine aktuellen Verkehrsdaten vor. Behaupte weder freie Fahrt noch einen Stau; benenne die fehlende Datenlage.", listOf("keine", "verkehrsdaten"), "Keine Live-Daten"),
        TrainingCase("Ein bestätigter Stau liegt 4 Kilometer voraus und verursacht 8 Minuten Verzögerung. Fasse nur diese Fakten zusammen.", listOf("stau", "4", "kilometer", "8", "minuten"), "Stauzusammenfassung"),
        TrainingCase("Eine Gefahrenmeldung ist abgelaufen. Behandle sie nicht als aktuelle Gefahr und sage, dass ihr Status veraltet ist.", listOf("abgelaufen", "veraltet"), "Meldungsalter"),
        TrainingCase("Für eine Umleitung fehlen verlässliche Verkehrsdaten. Erkläre, dass die Route nicht allein auf Vermutungen geändert wird.", listOf("verkehrsdaten", "vermutung"), "Umleitungsentscheidung"),
    ),
)

internal data class AiTrainingSession(
    val id: String,
    val trackId: String,
    val total: Int,
    val completed: Int,
    val passed: Int,
    val averageLatencyMs: Long,
    val status: String,
    val requiresCharging: Boolean,
    val maxTokens: Int,
    val customFocus: String,
    val startedAt: Long,
    val message: String,
)

internal data class AiTrainingResult(
    val trackId: String,
    val caseLabel: String,
    val latencyMs: Long,
    val passed: Boolean,
    val recordedAt: Long,
)

internal data class AiTrainingGeneratedCase(
    val id: String,
    val trackId: String,
    val label: String,
    val prompt: String,
    val requiredTerms: List<String>,
    val qualityScore: Int,
    val status: String,
    val createdAt: Long,
)

internal object AiTrainingCaseReview {
    fun parseAndScore(
        raw: String,
        track: AiTrainingTrack,
        existing: List<AiTrainingGeneratedCase>,
        now: Long,
    ): AiTrainingGeneratedCase? = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start)
        val json = JSONObject(raw.substring(start, end + 1))
        val label = json.getString("label").trim()
        val prompt = json.getString("prompt").trim()
        val termsArray = json.getJSONArray("requiredTerms")
        require(label.length in 3..60 && prompt.length in 40..400 && termsArray.length() in 2..4)
        val terms = (0 until termsArray.length()).map { termsArray.getString(it).trim().lowercase() }
        require(terms.all { it.length in 2..32 && it in prompt.lowercase() })
        val lower = prompt.lowercase()
        require(listOf("https://", "http://", "telefonnummer", "kennzeichen", "adresse:", "fahre jetzt", "biege jetzt")
            .none { it in lower })
        require(!Regex("""-?\\d{1,3}\\.\\d{3,}""").containsMatchIn(prompt))
        val normalized = lower.replace(Regex("[^a-zäöüß0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        require(existing.none { it.trackId == track.id &&
            it.prompt.lowercase().replace(Regex("[^a-zäöüß0-9 ]"), " ").replace(Regex("\\s+"), " ").trim() == normalized })
        val topic = when (track) {
            AiTrainingTrack.Vocabulary -> listOf("begriff", "erkläre", "unterschied", "wort")
            AiTrainingTrack.Voice -> listOf("ansage", "formuliere", "sprich", "richtung")
            AiTrainingTrack.RouteIntent -> listOf("ziel", "vermeide", "zwischenstopp", "wunsch")
            AiTrainingTrack.Maneuvers -> listOf("abbieg", "ausfahrt", "spur", "beschilderung")
            AiTrainingTrack.GpsSafety -> listOf("gps", "position", "messung", "genauigkeit")
            AiTrainingTrack.Traffic -> listOf("stau", "verkehrsdaten", "gefahr", "meldung")
        }
        val score = 55 + (if (topic.any { it in lower }) 25 else 0) + (if (terms.distinct().size == terms.size) 20 else 0)
        require(score >= 80)
        AiTrainingGeneratedCase(java.util.UUID.randomUUID().toString(), track.id, label, prompt, terms.distinct(), score, "Zur Prüfung", now)
    }.getOrNull()
}

internal object AiTrainingPrioritizer {
    fun nextCase(track: AiTrainingTrack, cases: List<TrainingCase>, allResults: List<AiTrainingResult>): TrainingCase {
        require(cases.isNotEmpty())
        val results = allResults.filter { it.trackId == track.id }
        return cases.withIndex().maxWithOrNull(
            compareBy<IndexedValue<TrainingCase>> { item ->
                val recent = results.filter { it.caseLabel == item.value.label }.takeLast(12)
                if (recent.isEmpty()) 1.25
                else {
                    val rate = recent.count { it.passed }.toDouble() / recent.size
                    (1.0 - rate) + if (recent.size < 4) 0.20 else 0.0
                }
            }.thenBy { item ->
                -(results.lastOrNull { it.caseLabel == item.value.label }?.recordedAt ?: 0L)
            }.thenBy { -item.index }
        )!!.value
    }

    fun nextTrack(allResults: List<AiTrainingResult>): AiTrainingTrack =
        AiTrainingTrack.entries.minWithOrNull(
            compareBy<AiTrainingTrack> { track ->
                val recent = allResults.filter { it.trackId == track.id }.takeLast(30)
                if (recent.isEmpty()) -1.0 else recent.count { it.passed }.toDouble() / recent.size
            }.thenBy { track -> allResults.count { it.trackId == track.id } }
        ) ?: AiTrainingTrack.RouteIntent
}

internal object AiTrainingEvaluationPolicy {
    fun passes(answer: String, requiredTerms: List<String>): Boolean {
        if (answer.isBlank() || requiredTerms.isEmpty()) return false
        val normalized = answer.lowercase()
        return requiredTerms.all { term -> term.lowercase() in normalized }
    }
}

internal object AiTrainingStore {
    private const val PREFS = "ai_training_center_v1"
    private const val KEY_SESSION = "session"
    private const val KEY_RESULTS = "results"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun currentSession(context: Context): AiTrainingSession? =
        runCatching { prefs(context).getString(KEY_SESSION, null)?.let(::decodeSession) }.getOrNull()

    fun begin(context: Context, session: AiTrainingSession) {
        prefs(context).edit().putString(KEY_SESSION, encodeSession(session).toString()).apply()
    }

    fun update(context: Context, transform: (AiTrainingSession) -> AiTrainingSession) {
        synchronized(this) {
            val current = currentSession(context) ?: return
            begin(context, transform(current))
        }
    }

    fun record(context: Context, result: AiTrainingResult) {
        synchronized(this) {
            val preferences = prefs(context)
            val rows = runCatching { JSONArray(preferences.getString(KEY_RESULTS, "[]")) }.getOrElse { JSONArray() }
            rows.put(JSONObject().apply {
                put("trackId", result.trackId)
                put("caseLabel", result.caseLabel)
                put("latencyMs", result.latencyMs)
                put("passed", result.passed)
                put("recordedAt", result.recordedAt)
            })
            val bounded = JSONArray()
            for (index in (rows.length() - 300).coerceAtLeast(0) until rows.length()) bounded.put(rows.getJSONObject(index))
            preferences.edit().putString(KEY_RESULTS, bounded.toString()).apply()
        }
    }

    fun results(context: Context): List<AiTrainingResult> {
        val rows = runCatching { JSONArray(prefs(context).getString(KEY_RESULTS, "[]")) }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { index ->
            runCatching {
                rows.getJSONObject(index).let {
                    AiTrainingResult(
                        trackId = it.getString("trackId"),
                        caseLabel = it.getString("caseLabel"),
                        latencyMs = it.getLong("latencyMs"),
                        passed = it.getBoolean("passed"),
                        recordedAt = it.getLong("recordedAt"),
                    )
                }
            }.getOrNull()
        }
    }

    private fun encodeSession(s: AiTrainingSession) = JSONObject().apply {
        put("id", s.id); put("trackId", s.trackId); put("total", s.total); put("completed", s.completed)
        put("passed", s.passed); put("averageLatencyMs", s.averageLatencyMs); put("status", s.status)
        put("requiresCharging", s.requiresCharging); put("maxTokens", s.maxTokens)
        put("customFocus", s.customFocus); put("startedAt", s.startedAt); put("message", s.message)
    }

    private fun decodeSession(json: String): AiTrainingSession {
        val o = JSONObject(json)
        return AiTrainingSession(
            id = o.getString("id"), trackId = o.getString("trackId"), total = o.getInt("total"),
            completed = o.getInt("completed"), passed = o.getInt("passed"),
            averageLatencyMs = o.getLong("averageLatencyMs"), status = o.getString("status"),
            requiresCharging = o.optBoolean("requiresCharging", true), maxTokens = o.optInt("maxTokens", 128),
            customFocus = o.optString("customFocus"), startedAt = o.getLong("startedAt"),
            message = o.optString("message"),
        )
    }
}

internal object AiTrainingScheduler {
    const val BATCH_SIZE = 6

    fun workName(sessionId: String) = "ai_training_$sessionId"

    fun enqueue(context: Context, session: AiTrainingSession, append: Boolean) {
        val startIndex = session.completed
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiresCharging(session.requiresCharging)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        val request = OneTimeWorkRequestBuilder<AiTrainingWorker>()
            .setInputData(
                Data.Builder()
                    .putString(KEY_SESSION_ID, session.id)
                    .putString(KEY_TRACK_ID, session.trackId)
                    .putInt(KEY_TOTAL, session.total)
                    .putInt(KEY_START_INDEX, startIndex)
                    .putInt(KEY_MAX_TOKENS, session.maxTokens)
                    .putString(KEY_CUSTOM_FOCUS, session.customFocus)
                    .putBoolean(KEY_REQUIRES_CHARGING, session.requiresCharging)
                    .build()
            )
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(workName(session.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(session.id),
            if (append) ExistingWorkPolicy.APPEND else ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    internal const val KEY_SESSION_ID = "session_id"
    internal const val KEY_TRACK_ID = "track_id"
    internal const val KEY_TOTAL = "total"
    internal const val KEY_START_INDEX = "start_index"
    internal const val KEY_MAX_TOKENS = "max_tokens"
    internal const val KEY_CUSTOM_FOCUS = "custom_focus"
    internal const val KEY_REQUIRES_CHARGING = "requires_charging"
}

class AiTrainingWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val sessionId = inputData.getString(AiTrainingScheduler.KEY_SESSION_ID) ?: return Result.failure()
        val track = AiTrainingTrack.fromId(inputData.getString(AiTrainingScheduler.KEY_TRACK_ID).orEmpty())
        val total = inputData.getInt(AiTrainingScheduler.KEY_TOTAL, 0).coerceIn(1, 1200)
        val startIndex = inputData.getInt(AiTrainingScheduler.KEY_START_INDEX, 0).coerceIn(0, total)
        val maxTokens = inputData.getInt(AiTrainingScheduler.KEY_MAX_TOKENS, 128).coerceIn(32, 512)
        val focus = inputData.getString(AiTrainingScheduler.KEY_CUSTOM_FOCUS).orEmpty().take(500)
        if (startIndex >= total) {
            AiTrainingStore.update(applicationContext) { it.copy(status = "Abgeschlossen", message = "Trainingslauf beendet.") }
            return Result.success()
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                AiTrainingStore.update(applicationContext) {
                    it.copy(status = "Wartet auf Abkühlung", message = "Der Lauf pausiert bei hoher Gerätetemperatur.")
                }
                return Result.retry()
            }
        }

        val model = File(applicationContext.filesDir, MODEL_FILE)
        if (!model.isFile || sha256(model) != MODEL_SHA256) {
            AiTrainingStore.update(applicationContext) {
                it.copy(status = "Fehler", message = "Verifiziertes lokales Modell fehlt. Öffne zuerst den KI-Gerätetest und starte danach den Lauf erneut.")
            }
            return Result.failure()
        }

        val engine = runCatching { JniSocialAiNativeEngine() }.getOrElse {
            AiTrainingStore.update(applicationContext) { s -> s.copy(status = "Fehler", message = "Lokale native KI-Laufzeit ist in diesem Build nicht verfügbar.") }
            return Result.failure()
        }
        val cases = trainingCases[track].orEmpty()
        var completed = startIndex
        try {
            check(engine.loadModel(model.absolutePath, 4096)) { "Lokales Modell konnte nicht geladen werden." }
            val end = (startIndex + AiTrainingScheduler.BATCH_SIZE).coerceAtMost(total)
            while (completed < end && !isStopped) {
                val scenario = AiTrainingPrioritizer.nextCase(track, cases, AiTrainingStore.results(applicationContext))
                val started = android.os.SystemClock.elapsedRealtime()
                val answer = engine.generate(buildPrompt(scenario.prompt, focus), maxTokens).trim()
                val latency = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
                val passed = AiTrainingEvaluationPolicy.passes(answer, scenario.requiredTerms)
                AiTrainingStore.record(applicationContext, AiTrainingResult(track.id, scenario.label, latency, passed, System.currentTimeMillis()))
                completed += 1
                val sessionResults = AiTrainingStore.results(applicationContext).filter {
                    it.trackId == track.id && it.recordedAt >= (AiTrainingStore.currentSession(applicationContext)?.startedAt ?: 0L)
                }
                val passCount = sessionResults.count { it.passed }
                val average = sessionResults.map { it.latencyMs }.average().takeIf { it.isFinite() }?.toLong() ?: latency
                AiTrainingStore.update(applicationContext) {
                    it.copy(completed = completed, passed = passCount, averageLatencyMs = average, status = "Läuft", message = scenario.label)
                }
                setProgressAsync(Data.Builder().putInt("completed", completed).putInt("total", total).putString("scenario", scenario.label).build())
            }
        } catch (error: Throwable) {
            AiTrainingStore.update(applicationContext) { it.copy(status = "Fehler", message = error.message?.take(240) ?: "Lokaler Trainingslauf fehlgeschlagen.") }
            return Result.failure()
        } finally {
            runCatching { engine.unloadModel() }
        }

        if (isStopped) return Result.success()
        if (completed >= total) {
            AiTrainingStore.update(applicationContext) { it.copy(completed = total, status = "Abgeschlossen", message = "Regelbasierte Auswertung vollständig. Modellgewichte wurden nicht verändert.") }
            return Result.success()
        }
        val session = AiTrainingStore.currentSession(applicationContext) ?: return Result.failure()
        AiTrainingScheduler.enqueue(applicationContext, session, append = true)
        return Result.success()
    }

    private fun buildPrompt(task: String, focus: String): String {
        val extra = focus.takeIf { it.isNotBlank() }?.let { "\nZusätzlicher Übungsschwerpunkt: $it" }.orEmpty()
        return "<|im_start|>system\nDu bist ein knapper, sicherheitsbewusster deutscher Routing-Assistent. Erfinde keine Positionen, Abbiegespuren, Verkehrsereignisse oder Routenfakten. Bei widersprüchlichen oder fehlenden Daten benenne die Unsicherheit. Dieser Text ist eine isolierte Übung: löse keine Navigation aus und führe keine Aktion aus.$extra<|im_end|>\n<|im_start|>user\n$task<|im_end|>\n<|im_start|>assistant\n"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MINIMUM_FREE_MEMORY_BYTES = 1_024L * 1024 * 1024
        private const val MODEL_FILE = "g620-qwen2.5-1.5b-q4_k_m.gguf"
        private const val MODEL_SHA256 = "1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370"
    }
}

private fun Long?.orZero() = this ?: 0L
