# Nachtentwicklung – 2026-11-19 (23:00 CET)

## Ausgangslage
- Branch: `fix/verified-training-pipeline`; Vergleich nach Abschluss `7fc1db1b98473da6bac20676b6441319634897ed...fix/verified-training-pipeline` = identical, 0 ahead/behind.
- Gelesen: `docs/RECOVERY_RELEASE_R1_2026-11-17.md`, `docs/AUTONOMOUS_ENGINEERING_R2_2026-11-17.md`, `docs/NIGHT_DEVELOPMENT_2026-11-18.md`. R1-P0 hat Vorrang.
- PR #55: geänderte Dateinamen über GitHub-Connector abrufbar, aber Status/Draft-Flag in diesem Lauf nicht zuverlässig abrufbar. Vorherige Übergabe bezeichnet ihn als Draft/DO NOT MERGE; keine gegenteilige Behauptung.
- CI: Der Commit-gebundene Workflow-Abruf für beide neuen Commits gab **keine Runs** zurück; das ist kein grüner Testnachweis. Der Endpoint liefert nur die erste Seite von PR-getriggerten Runs. Erneute Prüfung erforderlich.

## Änderungen auf Entwicklungsbranch
1. `bd809ea9a35dd7d774440f91fa77075dafa9dfaa` – `NavigationVoicePlaybackRuntime.kt`: Ein fallback-Manöver ohne Shape-Indizes darf nur bei vorheriger Nähe zum Manöverende (<=60 m) und großem positiven Distanzsprung (>75 m) die Playback-Identität wechseln. Damit wird ein GPS-Sprung 180 m → 300 m nicht als neues identisch formuliertes Manöver interpretiert. Kein Eingriff in die Route.
2. `7fc1db1b98473da6bac20676b6441319634897ed` – `NavigationVoicePlaybackRuntimeTest.kt`: Regression für 180 → 300 → 180 m ohne Shape-Indizes, erwartet genau eine Prepare-Ansage. Bestehender Test für 35 → 200 → 35 m als legitimer Manöverwechsel bleibt erhalten.

## Verifikation und Freigabe
- GitHub-Schreibzugriff und die beiden Commits bestätigt; Branch nach dem Testcommit exakt auf `7fc1db1b...` nachgewiesen.
- Kein lokaler Gradle-Lauf, keine CI-Freigabe der neuen Commits, kein Pixel-10-Pro-Test, keine neue APK oder SHA256 verifiziert.
- APK-Fallback: Frühere Übergabe nennt eine erfolgreich gebaute Debug-APK aus einem älteren Stand, aber kein aktuell heruntergeladenes, SHA256-verifiziertes Release-Artefakt. Nicht als fahrfreigegeben kennzeichnen.
- **R1 RC Gate: NO-GO bis CI + Offline-Karten + Links/Rechts + Voice + GPS/WLAN + Gerätetest geprüft sind.**
- Rollback: `git revert 7fc1db1b98473da6bac20676b6441319634897ed` und danach `git revert bd809ea9a35dd7d774440f91fa77075dafa9dfaa` nur auf Entwicklungsbranch nach Review. Bestehende Daten/VM unberührt.

## Acht priorisierte Folgepakete
1. CI für aktuellen HEAD erneut prüfen; bei roten Gates Logs analysieren und Fix mit Regression.
2. Debug-APK für exakten Commit abrufen, Binär-SHA256 und Installierbarkeit verifizieren; keine öffentliche Freigabe.
3. Offline DACH-Tile-Import, Persistenz, Neustart und Routing ohne WLAN testen.
4. Links/Rechts-/Ausfahrt-Manöver mit Valhalla-Shape-Indizes und Richtungs-Fixtures prüfen.
5. Voice-Scheduler: queued/now, Manöverwechsel, GPS-Jitter und tatsächliche TTS-Zeitpunkte; Regressionen erweitern.
6. GPS-Heading, Reroute und WLAN-Verlust/Fallback deterministisch und auf Pixel 10 Pro prüfen.
7. Erst nach P0: Journey M1-M4 (echte POI-Evidenz, gleiche Valhalla-Version, <=600s, Opt-in-Sync), KI advisory-only und Feature-Flag.
8. Freitag 20.11. RC Go/No-Go mit Installations-/Rollback-Anleitung, priorisierten Restfehlern und verifizierten Artefakten; Samstag 21.11. Gerätetest.

Keine Tests deaktiviert, kein Merge, kein Oracle-Deployment, keine Secrets/Kosten/Datenlöschung. R2/R3 bleiben nachrangig.
