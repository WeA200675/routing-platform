# Lokale KI und Pixel-10-Pro-Test

## Was lokal auf dem Gerät läuft

Der G6-Kandidatenlauf baut ein installierbares Android-Paket mit der fest gepinnten llama.cpp-Laufzeit und Qwen2.5-1.5B-Instruct Q4_K_M. Das Modell hat 986.048.768 Bytes und wird im Build anhand seiner SHA-256 geprüft. Inference und natürlichsprachige Eingabe laufen damit lokal und benötigen keinen KI-Hub. Die Produktionsroute wird weiterhin von der deterministischen Routing-Engine berechnet und validiert.

Der normale Android-CI-Smoke-Build enthält absichtlich weder das Modell noch die lokale native KI-Laufzeit. Für einen lokalen Gerätetest wird deshalb der separate `g620-physical-device-test`-Artifact aus demselben erfolgreichen **P1-P36 Production Candidate**-Lauf verwendet. Dieses Debug-Testpaket ist ein Prüfbuild, kein produktionssigniertes Release.

## Lokales Lernen

Explizite 👍/👎-Bewertungen für Zwischenziel-Kategorien aktualisieren ein kleines lokales Bayes-Präferenzmodell. Für jede Kategorie werden nur die Anzahl hilfreicher und nicht hilfreicher Bewertungen gespeichert. Daraus wird mit einem neutralen Beta(1, 1)-Prior die geschätzte Wahrscheinlichkeit berechnet, dass eine Kategorie hilfreich ist. Die App verwendet diesen Wert, um bevorzugte Zwischenziel-Kategorien zu sortieren.

Die Werte sind profilbezogen und bleiben im App-Speicher. Es werden dafür keine Ortsnamen, Ziele, Koordinaten oder Rohfahrten gespeichert oder übertragen. Wird das Lernen ausgeschaltet, löscht die App die dazugehörigen Präferenzwerte.

Dieses Verfahren trainiert **nicht** die Qwen-Sprachmodellgewichte. Das Sprachmodell bleibt das unveränderte, geprüfte Basismodell. Die App passt nur das kleine Präferenzmodell an explizite Rückmeldungen an. Die optionale Hub-LoRA-Funktion ist davon getrennt und trainiert Gewichte extern auf dem Hub-Rechner.

## Pixel 10 Pro: physischer Inferenztest

Der CI-Gerätetest ist ein manueller Android-Gerätetest; CI allein kann keine thermische oder Laufzeitmessung auf deinem Pixel belegen.

1. Öffne im GitHub-Actions-Lauf den Artifact `g620-physical-device-test` und lade genau den Artifact des gewünschten Candidate-Laufs.
2. Prüfe `g620-field-test.apk.sha256` gegen den APK-Hash.
3. Aktiviere auf dem Pixel USB-Debugging und verbinde es mit einem Rechner, auf dem Android Platform Tools installiert sind.
4. Installiere den Field-Test-Build und starte die Prüfansicht:

   ```sh
   adb devices -l
   adb install -r g620-field-test.apk
   adb shell am start -n org.routingplatform.app/.ai.SocialAiDeviceTestActivity
   ```

5. Tippe auf **G6 Kompletttest starten**. Ein erfolgreicher Bericht zeigt tatsächliche Modell- und Runtime-SHA-256, lokale Antwort, Erstinferenz, Unload/Reload-Recovery, zweite Inferenz, Gesamtdauer und verfügbaren RAM. Die Prüfung führt keinen Netzwerk-Fallback aus.
6. Teste anschließend in der App das lokale KI-Gespräch und die Zwischenziel-Bewertungen. Prüfe, dass eine neue Bewertung die lokale Kategorien-Reihenfolge verändert. Deaktiviere das Lernen und prüfe, dass gespeicherte Präferenzdaten entfernt werden.

Bewahre den Bericht mit der exakten Candidate-Quell-SHA auf. Ein grüner CI-Lauf ist kein Ersatz für den ausgeführten Pixel-Test.

## Grenzen und Rückfälle

- Wenn Modell, Runtime-SHA, Speicher oder Inferenz fehlschlagen, meldet die lokale KI einen Fehler. Gesprächsinhalte werden nicht automatisch an den Hub gesendet.
- Hub-Gespräche und Hub-LoRA-Training sind nur über ihre getrennten, ausdrücklich betätigten Funktionen erreichbar.
- Die KI darf keine Abbiegung, Fahrspur, Gefahrenmeldung oder Verkehrsregel erfinden. Unvollständige oder unsichere Navigationsabsichten werden durch den bestehenden Intent- und Routing-Sicherheitsprozess abgelehnt oder zur Klärung zurückgegeben.
- Das lokale Präferenzmodell beeinflusst nur die Sortierung von Zwischenziel-Kategorien. Es ändert keine Fahrroute und keine sicherheitskritische Navigationsanweisung.
