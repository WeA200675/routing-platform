# R1 Release-Candidate Gate / Nachtentwicklung – 20.11.2026

## Entscheidung: NO-GO für eine fahrfreigegebene RC-APK
Stand dieser Übergabe: 20.11.2026, abends. R1 hat Vorrang vor Journey/R2/R3.

### Quellen und Branch
- Arbeitsbranch: `fix/verified-training-pipeline`; vor dieser Übergabe nachgewiesener Code-HEAD `c73a404c740c4d4c51272fc0c1b3a63460598e42` (Branch-Vergleich: identical).
- Gelesen: `docs/RECOVERY_RELEASE_R1_2026-11-17.md`, `docs/AUTONOMOUS_ENGINEERING_R2_2026-11-17.md`, `docs/NIGHT_DEVELOPMENT_2026-11-19.md`.
- PR #55: https://github.com/WeA200675/routing-platform/pull/55; Beschreibung sagt Draft und **DO NOT MERGE OR DEPLOY**. Kein Merge.
- Vorheriger Test-Commit `7fc1db1b98473da6bac20676b6441319634897ed`: GitHub Actions `Android and core verification` Run `44624430408` **failure**; Android CI und Core CI **cancelled**; Backend Safety und local-storage-stack **success**.
- Fehlerlog: `416 tests completed, 1 failed`, `NavigationVoicePlaybackRuntimeTest.fallbackGpsSpikeOutsideCompletionWindowDoesNotRepeatPrepare`, Assertion line 174. Kotlin kompiliert; kein Build-Toolchain-Fehler.

### Korrektur
- Commit `c73a404c740c4d4c51272fc0c1b3a63460598e42`: Testinvariante auf **genau eine Prepare-Ansage** präzisiert. Im Detailed-Modus darf die GPS-Distanz 180 -> 300 m eine zusätzliche **Early**-Stufe auslösen; der vorherige Test verbot fälschlich jede weitere Cue und schlug deshalb fehl. Produktionscode unverändert. Kein Test deaktiviert.
- GitHub-CI für diesen neuen Commit war bei der ersten Abfrage noch nicht verfügbar; **nicht als grün melden**. Ein grüner Lauf muss vor Promotion nachgewiesen werden.
- Reales TTS-Timing und GPS-Spikes bleiben ohne physische Testfahrt unbestätigt.

### Fallback APK (nicht RC)
- Letzter in der Übergabe nachgewiesener fünfmal grüner Basis-Commit: `2d1653c2c0e49435ea9d1ec7e3c3547386768cf2`.
- Android CI: https://github.com/WeA200675/routing-platform/actions/runs/44161703418
- Artifact `routing-platform-debug-apk`, ID `14363468060`, **ZIP-Archiv** 53,326,687 Bytes, GitHub-Artifact-Digest `sha256:8f2c3bf9748773cc110e6a25cb2f564abd6b9ed2567470f587a8a3d32b6436f9`. **Dies ist der Artifact/ZIP-Digest, nicht der APK-Datei-SHA256.** Das ZIP wurde hier nicht heruntergeladen oder auf Android installiert; die APK-Byteprüfsumme bleibt offen.
- Der Basis-Build ist ein technischer Fallback zur manuellen Prüfung, **keine Freigabe für Straßenverkehr**.

### P0 Testanleitung Pixel 10 Pro – nur in sicherer Testumgebung
1. Artifact des exakten GitHub-Laufs herunterladen, ZIP-Digest gegen GitHub prüfen, APK extrahieren, SHA256 der tatsächlichen APK berechnen und Dateinamen/Commit protokollieren.
2. APK in Testumgebung installieren, Start und Berechtigungen prüfen; bestehende funktionierende App/Daten nicht überschreiben ohne vorherige Sicherung.
3. DACH-Offline-Karte importieren, App beenden/neustarten, Netzwerk ausschalten, bekannte Teststrecke berechnen und Navigation starten; sicherstellen, dass kein Cloud-Routing benötigt wird.
4. Links/Rechts und Autobahnausfahrten mit versionierten Valhalla-Manövern, Shape-Indizes und reproduzierbaren Fixtures prüfen.
5. Prepare/Now/Repeat-Ansagen inklusive 180 -> 300 -> 180 m GPS-Jitter, Warteschlange und Manöverwechsel testen.
6. WLAN während einer Testnavigation ausschalten; lokale Route muss erhalten bleiben, Fallback darf keine vorhandenen Offline-Karten ignorieren.
7. GPS-Heading, Kartenrotation, Reroute, Hintergrund/Vordergrund und Neustart prüfen; nur mit Beifahrer oder auf sicherem Testgelände bedienen.
8. Gerätetest dokumentieren (Pixel 10 Pro, Android-Version, App-Commit, Kartenversion, Pass/Fail, anonymisierte Evidenz). Ohne diese Nachweise **NO-GO**.

### Priorisierte Restfehler / acht Folgepakete
1. **P0:** CI für `c73a404` und den abschließenden Dokumentations-HEAD abwarten; rote Android-Tests anhand Logs gezielt korrigieren.
2. **P0:** Aktuelles Debug-APK-Artefakt samt echten APK-Bytes und SHA256 nachweisen; Baseline-Artefakt als Rückfall behalten.
3. **P0:** Offline-Tile-Import, Persistenz, Valhalla-Routing und Neustart ohne Internet reproduzierbar testen.
4. **P0:** Links/Rechts und Ausfahrten anhand echter Manövertypen/Shape-Indizes testen.
5. **P0:** Sprachansagen (Early/Prepare/Now), GPS-Jitter, queued TTS und Reroute testen.
6. **P0:** WLAN-Abbruch, Fallback, GPS-Stabilität und sichere Pixel-Gerätetestabnahme; No-Go bis grüne Gates.
7. **P1:** SQLite/PostgreSQL-Sync, Opt-in, Quarantäne, SHA256-Receipts, Replay und Journey M1–M4 fail-closed absichern.
8. **P2:** WC-/Rastplatzassistent mit validierten Offline-POIs, exakter Valhalla-via-POI-ETA <=600 s, Consent, Cooldown und Feature-Flag; erst nach R1 P0.

### Rollback und Schutzmaßnahmen
- Keine Produktionsdateien, Datenbanken, Oracle-VM oder `main` verändert.
- Bei Regression den einzelnen Testcommit `c73a404` auf dem Entwicklungsbranch revertieren; für Installation den unveränderten Basis-Commit `2d1653c2` als Referenz behalten.
- Keine physische Fahrt, kein Produktivdeployment, keine APK-Veröffentlichung, keine Secrets/Kosten.
