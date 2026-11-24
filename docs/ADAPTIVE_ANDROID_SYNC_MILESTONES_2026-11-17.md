# Adaptive Android Sync – Meilensteine und Abnahme (2026-11-17)

Branch: `fix/verified-training-pipeline`. **Nicht für Produktion freigegeben.**

## M1 – Datenintegrität (teilweise implementiert)
- SQLiteOpenHelper-basierte FIFO statt SharedPreferences, atomare INSERT/DELETE, guarded ACK.
- Legacy-Snapshot-Migration mit Wiederaufnahme bei Unterbrechung.
- Bei voller Queue explizit fehlschlagen statt unbestätigte Daten zu überschreiben.
- **Offen:** Migrationstest auf Gerät; Multi-Prozess-Sperren; sichere Verschlüsselung und Speicherbudget.

## M2 – Hintergrundbetrieb (teilweise implementiert)
- WorkManager mit Netzwerkbedingung, periodischer und einmaliger eindeutiger Arbeit, exponentiellem Backoff.
- WorkManager plant Wiederaufnahme auch nach normalem App-Schließen und Geräte-Neustart.
- **Offen:** Start der Synchronisierung unabhängig von der Activity/Discovery-Lambda; sichere Tokenbereitstellung; Doze-, Force-Stop- und OEM-Tests.

## M3 – Ende-zu-Ende-Transport (offen)
- Server-Antwort muss einzelne Event-IDs und SHA-256-Digests attestieren; HTTP 202 allein reicht nicht.
- Client validiert alle IDs, Digests und `durable=true`; Wiederholungen bleiben idempotent.
- **Offen:** API-Vertrag v2, Auth-Enrollment via Android Keystore, 401/403-Pause, 409-Quarantäne, 429/503 Retry-After.
- API URL darf `/api` enthalten, Client normalisiert sie.
- Große Diagnose-Snapshots in atomare Batches <=256 Events und <=256 KiB aufteilen, ohne Datenverlust.

## M4 – Adaptive Steuerung und Datenschutz (offen)
- Zwei Prioritätsklassen mit fairer Alterung; keine Verdrängung alter Daten.
- Offline-Status, letzte erfolgreiche ACK, Queue-Bytes, Quarantäne und expliziter Export.
- Netzwerk-/Energie-Policy ohne Blockierung von Navigation, GPS oder Voice.
- Verschlüsselte lokale sensible Daten, TTL erst nach bestätigter Verarbeitung, redigierte Logs.

## M5 – Nachweisbare Abnahme (offen)
- Android Unit- und Instrumentation-Tests: Prozesskill, Reboot, Doze, Offline >24h, Netzwechsel, 401, 409, 429, 503, Timeout nach Commit, doppelte IDs.
- E2E mit isoliertem PostgreSQL, absichtlichen Verbindungsabbrüchen und Replay/Attestor.
- Alle GitHub CI-Workflows grün, Testbericht und signierte APK; danach erst Freigabe.

## Sicherheitsinvarianten
1. Keine Navigation hängt vom Trainingsbackend ab.
2. Keine Löschung lokaler Ereignisse ohne beweisbare, dauerhafte Bestätigung.
3. Keine automatischen produktiven Deployments oder Merges.
4. Fehlende Authentifizierung führt zu sicherem Upload-Stopp.
5. Android kann WorkManager-Ausführung verzögern; nach Force-Stop keine Laufzeitgarantie.

## Gegenwärtiger Stand
Code für M1/M2 ist in Entwicklung. M3–M5 sind **nicht abgeschlossen**. Eine vollständige Fertigmeldung setzt erfolgreiche CI und reale End-to-End-Tests voraus.
