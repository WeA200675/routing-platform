# Recovery Release R1 – Navigation auf den Funktionsstand Ende Oktober bringen

Datum: 2026-11-17. Arbeitsbranch: `fix/verified-training-pipeline`.
Referenzpunkte: `01554528e270bc95e1ecd71055293e135781f154` (28.10.), `03a04643540f2c7e9edfdaa7a0e90b803102fc12` (01.11.), älterer Feldtest `g620-physical-device-test`.
Der Vergleich `01554528...fix/verified-training-pipeline` zeigt 492 nachfolgende Commits. Das ist **kein** Nachweis, dass alle Features funktionieren.

## Leitplanken
- Keine pauschale Rücksetzung oder Löschung von Nutzer-/Trainingsdaten.
- Bestehende APK und bekannte Referenzcommits unverändert als Rückfalloption.
- Entwicklung nur auf Branch; kein Merge nach main und kein Deployment auf Oracle ohne explizite Freigabe.
- Offline-Navigation muss ohne Cloud/LLM funktionieren; KI darf sicherheitsrelevante Manöver nicht ungeprüft überschreiben.
- Jedes Gate benötigt reproduzierbare Testartefakte; grüne CI ersetzt keinen realen Fahrtest.

## M0 – Inventur und reproduzierbare Baseline
1. Android, Routing, Offline-Tiles, GPS, Voice, KI, Trainingscenter, Cloud und Dashboard katalogisieren.
2. Build-/Workflow-/APK-Stände der Referenzcommits und aktuellen HEADs dokumentieren.
3. Verhalten Ende Oktober als Soll-Matrix definieren, verlorene und neue Funktionen differenzieren.
4. Risiken nach Blocker/High/Medium klassifizieren, mit Test für jeden Befund.
**Gate:** Featurematrix und nachvollziehbarer Build-Stand.

## M1 – Navigation und Offline-Fähigkeit vollständig wiederherstellen
1. Offline-Tile-Import, Persistenz, Kartenwahl und Neustart ohne Internet.
2. Valhalla-/JNI-Manövertypen, Links/Rechts, Abbiegegeometrie und Richtungsansagen.
3. Voice-Scheduler: Prepare/Now, letzte Abbiegung, keine um ein Manöver verzögerten Ansagen.
4. GPS-Smoothing, Heading, Kartenrotation, Jitter, Reroute und WLAN-Wechsel.
5. Routing-Fallback darf nie lokale verfügbare Karten ignorieren.
**Gate:** Unit-/Integrationstests und Offline-Smoke-APK, kein Navigations-Blocker.

## M2 – KI, Lernen und Training ohne Navigationsrisiko
1. KI-Routenlogik advisory-only; deterministischer Router bleibt autoritativ.
2. Training beginnt bei Routenstart, speichert getrennte Sitzungen und Fehlertrigger.
3. Ereignisse idempotent, SQLite-Outbox, WorkManager, Netzwerk-Recovery.
4. LLM-Laufzeit lokal/Cloud nur nach überprüfter Verfügbarkeit, Ressourcenlimit und Consent.
5. Trainingsdashboard zeigt reale, nicht synthetisch als live deklarierte Metriken.
**Gate:** Offline und Online E2E mit nachweisbaren Trainingsevents, kein Navigationseinfluss bei KI-Ausfall.

## M3 – Cloud-/Datenintegrität und Datenschutz
1. API-URL und Discovery, TLS, Auth-Token sicher im Keystore, 401/403 fail-closed.
2. Server-Receipts pro event_id + SHA-256; Android verifiziert die kanonische Prüfsumme, nicht nur IDs.
3. Batches <=256 Events und <=256 KiB, atomare ACKs, große Snapshots splitten.
4. 409-Konflikt quarantänisieren; 429/503 Retry-After, Timeout nach Server-Commit, Duplikate.
5. PostgreSQL/SQLite-Backends identische Verträge, Backups und Wiederherstellung testen.
**Gate:** Reproduzierbare E2E-Tests inklusive Prozesskill/Netzunterbrechung.

## M4 – UI, Testbarkeit und Release
1. Kartenstile, einklappbare Bedienelemente, Zwischenziele, Geschwindigkeitswarnung, Helligkeit, Versionsanzeige.
2. Trainingscenter und Statusanzeige inkl. Queue, letzter ACK, Backoff, Offline-Zustand.
3. Android CI, Core CI, Backend Safety, Instrumentation; Gerätetest mit Fahrt-Checkliste.
4. APK-Artefakt mit SHA-256, Versionsnummer, Installations-/Rollbackanleitung.
**Gate:** Alle CI grün + Gerätetest dokumentiert + Vergleich Soll/Ist ohne offene Blocker.

## Automatisierter Großlauf ohne tägliche Nutzereingriffe
- Nachtentwicklung arbeitet in kleinen, überprüfbaren Commits innerhalb der großen Meilensteine.
- Bei Testfehlern automatische Diagnose und gezielter Fix; niemals Tests deaktivieren, um grün zu erzeugen.
- Bei fehlenden Geheimnissen, physischem Gerät oder externen Freigaben den Punkt als blockiert markieren und unabhängige Arbeit fortsetzen.
- Morgenbericht: Commit, geprüfte Tests, neue Befunde, APK, Blocker, nächster Schritt.
- Maximal ein fokussierter Review-/Abnahmepunkt für den Nutzer; keine tägliche Handarbeit voraussetzen.

## Status
**Plan erstellt, keine Komplettabnahme.** M0-M4 erst nach den jeweiligen Gates als fertig markieren.
