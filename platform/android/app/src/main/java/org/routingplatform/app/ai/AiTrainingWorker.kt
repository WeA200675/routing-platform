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
import java.util.concurrent.TimeUnit\nimport java.util.UUID

internal enum class AiTrainingTrack(val id: String, val title: String, val detail: String) {
    Vocabulary("vocabulary", "Wortschatz", "Begriffe klar und natürlich erklären"),
    Voice("voice", "Ansagen", "Kurze, eindeutige Sprachführung"),
    RouteIntent("route_intent", "Routenwünsche", "Ziel, Vermeidung und Zwischenstopp erkennen"),
    Maneuvers("maneuvers", "Abbiegen & Ausfahrten", "Richtungsangaben verständlich formulieren"),
    ManeuverAlignment("maneuver_alignment", "Manöverabgleich", "Anzeige und Sprachansage auf Reihenfolge und Identität prüfen"),
    GpsSafety("gps_safety", "GPS & Sicherheit", "Einzelfehler nicht als bestätigte Abweichung behandeln"),
    Traffic("traffic", "Stau & Gefahren", "Fehlende Live-Daten offen benennen"),
    DataSources("data_sources", "Quellen & Datenabgleich", "Spur- und Schilddaten online finden und verifizieren"),
    HumorThemes("humor_themes", "Humor & Überraschung", "Aufmunternde Themen vorschlagen und sicher einsetzen");

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
        TrainingCase("Answer in English: identify the nearest restroom, fuel station and parking area. Use the words restroom, fuel and parking.", listOf("restroom", "fuel", "parking"), "POI English"),
        TrainingCase("Réponds en français : cherche des toilettes, une station-service et un parking. Utilise ces trois termes.", listOf("toilettes", "station-service", "parking"), "POI Français"),
        TrainingCase("Rispondi in italiano: trova un bagno, una stazione di servizio e un parcheggio. Usa tutti e tre i termini.", listOf("bagno", "stazione", "parcheggio"), "POI Italiano"),
        TrainingCase("Responde en español: busca un baño, una gasolinera y un aparcamiento. Usa las tres palabras.", listOf("baño", "gasolinera", "aparcamiento"), "POI Español"),
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
    AiTrainingTrack.ManeuverAlignment to listOf(
        TrainingCase("Anzeige und Sprachansage müssen dieselbe Manöver-ID und dieselben Shape-Indizes verwenden. Beschreibe die Prüfung.", listOf("manöver", "sprachansage", "shape"), "Identität von Anzeige und Sprache"),
        TrainingCase("Die Anzeige zeigt das aktuelle Manöver, die Sprachansage nennt das vorherige. Erkenne den Sequenzversatz und benenne ihn ausdrücklich.", listOf("anzeige", "sprachansage", "versatz"), "Ein-Manöver-Versatz"),
        TrainingCase("Richtung oder Straßenname der Sprachansage weichen vom angezeigten Routenmanöver ab. Erzeuge keine neue Richtung, sondern klassifiziere den Inhaltskonflikt.", listOf("richtung", "straßenname", "konflikt"), "Inhaltskonflikt"),
        TrainingCase("Eine Autobahnausfahrt ist in der Route rechts, aber die Sprachansage sagt links. Markiere den Widerspruch und verwende ausschließlich die berechnete Routenseite.", listOf("autobahnausfahrt", "rechts", "links", "widerspruch"), "Ausfahrtsprüfung"),
        TrainingCase("Prüfe die Zeitstempel von Anzeige und Sprachansage und erkenne, ob die Ansage nach dem Wechsel auf ein neues Manöver ausgelöst wurde.", listOf("zeitstempel", "wechsel", "manöver"), "Zeitliche Zuordnung"),
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
    AiTrainingTrack.HumorThemes to listOf(
        TrainingCase("Schlage drei eigenständige, freundliche Navigationsthemen vor. Nenne keine geschützte Figur und imitiere keine bekannte Stimme.", listOf("eigenständig", "freundlich", "themen"), "Originale Themen"),
        TrainingCase("Erkläre, dass ein humorvoller Vorspann die Abbiegerichtung niemals verändern darf und Sicherheitsansagen sachlich bleiben.", listOf("humorvoll", "abbiegerichtung", "sicherheitsansagen"), "Sicherheitsgrenze"),
        TrainingCase("Bewerte einen Themenvorschlag nach Zustimmung des Nutzers, Verständlichkeit, Quellenlage und Abschaltbarkeit.", listOf("zustimmung", "verständlichkeit", "quellenlage", "abschaltbarkeit"), "Themenbewertung"),
        TrainingCase("Wenn der Nutzer eine Figur aus Film oder Serie nennt, formuliere eine eigene thematische Alternative ohne Originaldialoge oder Stimmenkopie.", listOf("eigene", "alternative", "originaldialoge", "stimmenkopie"), "Rechtebewusste Alternative"),
    ),

    AiTrainingTrack.DataSources to listOf(
        TrainingCase("Für eine nächste Ausfahrt fehlen Spurdaten. Beschreibe die Suche nach einer aktuellen, straßengenau passenden Onlinequelle und prüfe Quelle, Zeitstempel und Fahrtrichtung, bevor die Angabe angezeigt wird.", listOf("onlinequelle", "zeitstempel", "fahrtrichtung"), "Spurdatenquelle prüfen"),
        TrainingCase("OpenStreetMap liefert turn:lanes für eine nahe Straße, aber die Fahrtrichtung passt nicht zur Route. Verwirf den Treffer und suche weiter nach der richtigen Richtungsfahrbahn.", listOf("openstreetmap", "fahrtrichtung", "verwerfen"), "Falsche Richtungsfahrbahn"),
        TrainingCase("Ein Routinggraph enthält keine Spurinformation. Nenne einen geordneten Ersatzweg über eine zugelassene Onlinequelle und verlange einen eindeutigen Straßen- und Kreuzungsabgleich.", listOf("routinggraph", "onlinequelle", "kreuzungsabgleich"), "Quellen-Fallback"),
        TrainingCase("Zwei Quellen widersprechen sich bei der Ausfahrtspur. Vergleiche Aktualität, Straßenidentität und Herkunft; gib bei ungelöstem Konflikt keine Spur als bestätigt aus.", listOf("aktualität", "herkunft", "bestätigt"), "Quellenkonflikt"),
        TrainingCase("Ein Onlineanbieter ist nicht erreichbar und der lokale Kartensatz ist veraltet. Kennzeichne die Spur als nicht verfügbar und nenne beide fehlgeschlagenen Quellen.", listOf("nicht verfügbar", "lokal", "online"), "Netzwerkausfall"),
        TrainingCase("Ein Kartentreffer nennt die Ausfahrt, aber keine Spurzuordnung. Trenne bestätigtes Schildziel von unbekannter Spur und erfinde keine Zuordnung.", listOf("schildziel", "spur", "unbekannt"), "Schild ohne Spurdaten"),
        TrainingCase("Für einen Onlinefund fehlen Lizenz, Attribution oder Aktualisierungsdatum. Erkläre, welche Prüfung vor der Übernahme fehlt und verwende die Angabe noch nicht.", listOf("lizenz", "attribution", "aktualisierungsdatum"), "Nutzungsrechte prüfen"),
        TrainingCase("Eine Quelle liefert Spurpfeile ohne nachvollziehbare Straßen- oder Kreuzungszuordnung. Beschreibe den Geometrie- und Richtungsabgleich, bevor die KI daraus eine Anzeige erstellt.", listOf("geometrie", "richtungsabgleich", "zuordnung"), "Geometrieabgleich"),
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
    val trackIds: List<String> = emptyList(),
)

internal data class AiTrainingResult(
    val trackId: String,
    val caseLabel: String,
    val latencyMs: Long,
    val passed: Boolean,
    val recordedAt: Long,
    val sessionId: String = "",
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
        require(!Regex("""-?\d{1,3}\.\d{3,}""").containsMatchIn(prompt))
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
            AiTrainingTrack.DataSources -> listOf("quelle", "online", "aktualität", "attribution", "openstreetmap")
            AiTrainingTrack.HumorThemes -> listOf("thema", "humor", "sicherheitsansagen", "quelle", "eigenständig")
            AiTrainingTrack.ManeuverAlignment -> listOf("manöver", "sprachansage", "anzeige", "versatz", "zeitstempel")
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
            }.thenBy { item -> -item.index }
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
    private const val KEY_SESSION_HISTORY = "session_history"
    private const val KEY_RESULTS = "results"
    private const val KEY_GENERATED_CASES = "generated_cases"


    fun generatedCases(context: Context): List<AiTrainingGeneratedCase> {
        val rows = runCatching { JSONArray(prefs(context).getString(KEY_GENERATED_CASES, "[]")) }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { index -> runCatching {
            val item = rows.getJSONObject(index)
            val terms = item.getJSONArray("requiredTerms")
            AiTrainingGeneratedCase(
                id = item.getString("id"), trackId = item.getString("trackId"),
                label = item.getString("label"), prompt = item.getString("prompt"),
                requiredTerms = (0 until terms.length()).map { terms.getString(it) },
                qualityScore = item.getInt("qualityScore"), status = item.getString("status"),
                createdAt = item.getLong("createdAt"),
            )
        }.getOrNull() }
    }

    fun recordGeneratedCase(context: Context, draft: AiTrainingGeneratedCase) {
        synchronized(this) {
            val current = generatedCases(context)
            if (current.any { it.prompt.equals(draft.prompt, ignoreCase = true) }) return
            saveGeneratedCases(context, (current + draft).takeLast(60))
        }
    }

    fun updateGeneratedCase(context: Context, replacement: AiTrainingGeneratedCase) {
        synchronized(this) {
            val updated = generatedCases(context).map { if (it.id == replacement.id) replacement else it }
            saveGeneratedCases(context, updated)
        }
    }

    fun setGeneratedCaseStatus(context: Context, id: String, status: String) {
        require(status in setOf("Zur Prüfung", "Freigegeben", "Zurückgestellt", "Verworfen"))
        synchronized(this) {
            val changed = generatedCases(context).map { if (it.id == id) it.copy(status = status) else it }
            saveGeneratedCases(context, changed)
        }
    }

    fun casesForTrack(context: Context, track: AiTrainingTrack): List<TrainingCase> =
        trainingCases[track].orEmpty() + generatedCases(context)
            .filter { it.trackId == track.id && it.status == "Freigegeben" }
            .map { TrainingCase(it.prompt, it.requiredTerms, it.label) }

    private fun saveGeneratedCases(context: Context, cases: List<AiTrainingGeneratedCase>) {
        val rows = JSONArray()
        cases.forEach { item ->
            rows.put(JSONObject().apply {
                put("id", item.id); put("trackId", item.trackId); put("label", item.label)
                put("prompt", item.prompt); put("requiredTerms", JSONArray(item.requiredTerms))
                put("qualityScore", item.qualityScore); put("status", item.status); put("createdAt", item.createdAt)
            })
        }
        prefs(context).edit().putString(KEY_GENERATED_CASES, rows.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)


    fun currentSession(context: Context): AiTrainingSession? =
        runCatching { prefs(context).getString(KEY_SESSION, null)?.let(::decodeSession) }.getOrNull()

    fun sessionHistory(context: Context): List<AiTrainingSession> {
        val rows = runCatching { JSONArray(prefs(context).getString(KEY_SESSION_HISTORY, "[]")) }
            .getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { index ->
            runCatching { decodeSession(rows.getJSONObject(index).toString()) }.getOrNull()
        }
    }

    fun begin(context: Context, session: AiTrainingSession) {
        synchronized(this) {
            val preferences = prefs(context)
            val editor = preferences.edit()
            val previous = currentSession(context)
            if (previous != null && previous.id != session.id) {
                val archivedPrevious = if (
                    previous.status in setOf("Läuft", "Wartet auf Bedingungen", "Wartet auf Abkühlung", "Wartet auf freien Speicher")
                ) {
                    previous.copy(
                        status = "Durch neuen Auftrag abgelöst",
                        message = "Der Auftrag wurde durch eine neue Reihe ersetzt; bisherige Ergebnisse sind archiviert.",
                    )
                } else previous
                val history = (sessionHistory(context).filterNot { it.id == archivedPrevious.id } + archivedPrevious).takeLast(200)
                val rows = JSONArray()
                history.forEach { rows.put(encodeSession(it)) }
                editor.putString(KEY_SESSION_HISTORY, rows.toString())
            }
            editor.putString(KEY_SESSION, encodeSession(session).toString()).apply()
        }
    }

    fun update(
        context: Context,
        expectedSessionId: String? = null,
        transform: (AiTrainingSession) -> AiTrainingSession,
    ) {
        synchronized(this) {
            val current = currentSession(context) ?: return
            if (expectedSessionId != null && current.id != expectedSessionId) return
            val updated = transform(current)
            prefs(context).edit().putString(KEY_SESSION, encodeSession(updated).toString()).apply()
        }
    }

    fun record(context: Context, result: AiTrainingResult) {
        synchronized(this) {
            val preferences = prefs(context)
            val rows = runCatching { JSONArray(preferences.getString(KEY_RESULTS, "[]")) }.getOrElse { JSONArray() }
            rows.put(JSONObject().apply {
                put("sessionId", result.sessionId)
                put("trackId", result.trackId)
                put("caseLabel", result.caseLabel)
                put("latencyMs", result.latencyMs)
                put("passed", result.passed)
                put("recordedAt", result.recordedAt)
            })
            val bounded = JSONArray()
            for (index in (rows.length() - 20000).coerceAtLeast(0) until rows.length()) bounded.put(rows.getJSONObject(index))
            preferences.edit().putString(KEY_RESULTS, bounded.toString()).apply()
        }
    }

    fun recordManeuverAlignment(context: Context, result: org.routingplatform.app.navigation.ManeuverAlignmentResult) {
        record(
            context,
            AiTrainingResult(
                trackId = AiTrainingTrack.ManeuverAlignment.id,
                caseLabel = result.status.name,
                latencyMs = 0L,
                passed = result.status == org.routingplatform.app.navigation.ManeuverAlignmentStatus.Aligned,
                recordedAt = System.currentTimeMillis(),
                sessionId = "navigation-alignment",
            ),
        )
    }

    fun results(context: Context): List<AiTrainingResult> {
        val rows = runCatching { JSONArray(prefs(context).getString(KEY_RESULTS, "[]")) }.getOrElse { JSONArray() }
        return (0 until rows.length()).mapNotNull { index ->
            runCatching {
                rows.getJSONObject(index).let {
                    AiTrainingResult(
                        sessionId = it.optString("sessionId", ""),
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
        put("trackIds", JSONArray(s.trackIds))
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
            trackIds = o.optJSONArray("trackIds")?.let { ids ->
                (0 until ids.length()).map { ids.getString(it) }
            }.orEmpty().ifEmpty { listOf(o.getString("trackId")) },
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
                    .putString(KEY_TRACK_IDS, JSONArray(session.trackIds.ifEmpty { listOf(session.trackId) }).toString())
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
    internal const val KEY_TRACK_IDS = "track_ids"
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
        val planTracks = runCatching {
            val ids = JSONArray(inputData.getString(AiTrainingScheduler.KEY_TRACK_IDS).orEmpty())
            (0 until ids.length()).map { AiTrainingTrack.fromId(ids.getString(it)) }.distinct()
        }.getOrDefault(listOf(track)).ifEmpty { listOf(track) }
        val maxTokens = inputData.getInt(AiTrainingScheduler.KEY_MAX_TOKENS, 128).coerceIn(32, 512)
        val focus = inputData.getString(AiTrainingScheduler.KEY_CUSTOM_FOCUS).orEmpty().take(500)
        if (startIndex >= total) {
            AiTrainingStore.update(applicationContext, sessionId) { it.copy(status = "Abgeschlossen", message = "Trainingslauf beendet.") }
            val preferences = applicationContext.getSharedPreferences("ai_training_center_v1", Context.MODE_PRIVATE)
            val autoLoop = preferences.getBoolean("auto_loop", true)
            if (autoLoop) {
                val finished = AiTrainingStore.currentSession(applicationContext)
                if (finished != null && finished.id == sessionId) {
                    val next = finished.copy(
                        id = UUID.randomUUID().toString(),
                        completed = 0,
                        passed = 0,
                        averageLatencyMs = 0L,
                        status = "Geplant",
                        startedAt = System.currentTimeMillis(),
                        message = "Nächster adaptiver Trainingslauf wird geplant.",
                    )
                    AiTrainingStore.begin(applicationContext, next)
                    AiTrainingScheduler.enqueue(applicationContext, next, append = false)
                }
            }
            return Result.success()
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
                AiTrainingStore.update(applicationContext, sessionId) {
                    it.copy(status = "Wartet auf Abkühlung", message = "Der Lauf pausiert bei hoher Gerätetemperatur.")
                }
                return Result.retry()
            }
        }

        val model = File(applicationContext.filesDir, MODEL_FILE)
        if (!model.isFile || sha256(model) != MODEL_SHA256) {
            AiTrainingStore.update(applicationContext, sessionId) {
                it.copy(status = "Fehler", message = "Verifiziertes lokales Modell fehlt. Öffne zuerst den KI-Gerätetest und starte danach den Lauf erneut.")
            }
            return Result.failure()
        }

        val engine = runCatching { JniSocialAiNativeEngine() }.getOrElse {
            AiTrainingStore.update(applicationContext, sessionId) { s -> s.copy(status = "Fehler", message = "Lokale native KI-Laufzeit ist in diesem Build nicht verfügbar.") }
            return Result.failure()
        }
        val cases = AiTrainingStore.casesForTrack(applicationContext, track)
        var completed = startIndex
        var generatedDraft: AiTrainingGeneratedCase? = null
        try {
            check(engine.loadModel(model.absolutePath, 4096)) { "Lokales Modell konnte nicht geladen werden." }
            if (startIndex == 0) {
                AiTrainingStore.generatedCases(applicationContext)
                    .filter { it.trackId == track.id && it.status == "Zurückgestellt" }
                    .minByOrNull { it.qualityScore }
                    ?.let { deferred ->
                        val answer = engine.generate(buildPrompt(deferred.prompt, ""), maxTokens.coerceAtMost(128))
                        val passed = AiTrainingEvaluationPolicy.passes(answer, deferred.requiredTerms)
                        val score = (deferred.qualityScore + if (passed) 8 else -12).coerceIn(0, 100)
                        AiTrainingStore.updateGeneratedCase(
                            applicationContext,
                            deferred.copy(
                                qualityScore = score,
                                status = if (score >= 80) "Zur Prüfung" else "Zurückgestellt",
                            ),
                        )
                    }
            }
            val end = (startIndex + AiTrainingScheduler.BATCH_SIZE).coerceAtMost(total)
            while (completed < end && !isStopped) {
                val scenarioTrack = planTracks[completed % planTracks.size]
                val scenarioCases = if (scenarioTrack == track) cases else
                    AiTrainingStore.casesForTrack(applicationContext, scenarioTrack)
                val scenario = AiTrainingPrioritizer.nextCase(scenarioTrack, scenarioCases, AiTrainingStore.results(applicationContext))
                val started = android.os.SystemClock.elapsedRealtime()
                val answer = engine.generate(buildPrompt(scenario.prompt, focus), maxTokens).trim()
                val latency = (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(0L)
                val passed = AiTrainingEvaluationPolicy.passes(answer, scenario.requiredTerms)
                AiTrainingStore.record(applicationContext, AiTrainingResult(
                    trackId = scenarioTrack.id,
                    caseLabel = scenario.label,
                    latencyMs = latency,
                    passed = passed,
                    recordedAt = System.currentTimeMillis(),
                    sessionId = sessionId,
                ))
                completed += 1
                val sessionResults = AiTrainingStore.results(applicationContext).filter {
                    it.sessionId == sessionId ||
                        (it.sessionId.isBlank() && it.trackId in planTracks.map { planned -> planned.id } &&
                            it.recordedAt >= (AiTrainingStore.currentSession(applicationContext)?.startedAt ?: 0L))
                }
                val passCount = sessionResults.count { it.passed }
                val average = sessionResults.map { it.latencyMs }.average().takeIf { it.isFinite() }?.toLong() ?: latency
                AiTrainingStore.update(applicationContext, sessionId) {
                    it.copy(completed = completed, passed = passCount, averageLatencyMs = average, status = "Läuft", message = scenario.label)
                }
                setProgressAsync(Data.Builder().putInt("completed", completed).putInt("total", total).putString("scenario", scenario.label).build())
            }
            if (!isStopped && completed >= total) {
                generatedDraft = runCatching {
                    val candidateJson = engine.generate(buildCaseDraftPrompt(track), 256)
                    AiTrainingCaseReview.parseAndScore(
                        candidateJson,
                        track,
                        AiTrainingStore.generatedCases(applicationContext),
                        System.currentTimeMillis(),
                    )
                }.getOrNull()
                generatedDraft?.let { AiTrainingStore.recordGeneratedCase(applicationContext, it) }
            }
        } catch (error: Throwable) {
            AiTrainingStore.update(applicationContext, sessionId) { it.copy(status = "Fehler", message = error.message?.take(240) ?: "Lokaler Trainingslauf fehlgeschlagen.") }
            return Result.failure()
        } finally {
            runCatching { engine.unloadModel() }
        }

        if (isStopped) return Result.success()
        if (completed >= total) {
            AiTrainingStore.update(applicationContext, sessionId) {
                it.copy(
                    completed = total,
                    status = "Abgeschlossen",
                    message = generatedDraft?.let { draft ->
                        "Lauf beendet. KI-Fall „${draft.label}“ mit ${draft.qualityScore}% wartet auf Prüfung. Modellgewichte unverändert."
                    } ?: "Lauf beendet. Kein prüfbarer KI-Fall erzeugt; Modellgewichte unverändert.",
                )
            }
            return Result.success()
        }
        val session = AiTrainingStore.currentSession(applicationContext) ?: return Result.failure()
        if (session.id != sessionId) return Result.success()
        AiTrainingScheduler.enqueue(applicationContext, session, append = true)
        return Result.success()
    }

    private fun buildCaseDraftPrompt(track: AiTrainingTrack): String =
        "<|im_start|>system\
Erzeuge genau einen abstrakten Offline-Testfall für den App-Bereich ${track.title}. Antworte ausschließlich als JSON-Objekt mit label, prompt und requiredTerms (2 bis 4 Strings). Die requiredTerms müssen im prompt vorkommen. Keine echten Orte, Koordinaten, Personen, URLs, persönlichen Daten oder ausführbaren Fahranweisungen. Keine echten oder behaupteten Live-Verkehrsdaten. Für Quellenfälle darfst du nur ausdrücklich im prompt bereitgestellte fiktive Metadaten auswerten; erfinde keine Quelle, Aktualität, Lizenz oder Spurzuordnung.\
<|im_end|>\
<|im_start|>user\
Erzeuge einen neuen, überprüfbaren, nicht trivialen Fall.\
<|im_end|>\
<|im_start|>assistant\
"

    private fun buildPrompt(task: String, focus: String): String {
        val extra = focus.takeIf { it.isNotBlank() }?.let { "
Zusätzlicher Übungsschwerpunkt: $it" }.orEmpty()
        return "<|im_start|>system
Du bist ein knapper, sicherheitsbewusster deutscher Routing-Assistent. Erfinde keine Positionen, Abbiegespuren, Verkehrsereignisse oder Routenfakten. Bei widersprüchlichen oder fehlenden Daten benenne die Unsicherheit. Dieser Text ist eine isolierte Übung: löse keine Navigation aus und führe keine Aktion aus.$extra<|im_end|>
<|im_start|>user
$task<|im_end|>
<|im_start|>assistant
"
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
