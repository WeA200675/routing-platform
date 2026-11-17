# Nachtentwicklung / R1-R3 Übergabe – 17.11.2026, abends

## Scope, Branch, Ausgangslage
- Repo: `WeA200675/routing-platform`; Branch `fix/verified-training-pipeline`.
- Ausgangs-HEAD: `2d1653c2c0e49435ea9d1ec7e3c3547386768cf2`; dessen fünf Workflows (Android CI, Android/Core verification, Core CI, Backend Safety, local-storage-stack) waren erfolgreich.
- PR #55: https://github.com/WeA200675/routing-platform/pull/55 – Draft mit ausdrücklichem DO NOT MERGE/DEPLOY und offenen Replay-/PostgreSQL-/Android-Gates. Vorherige Übergabe: `docs/NIGHT_DEVELOPMENT_2026-11-16.md`.
- Die Baseline-Android-CI `44161703418` veröffentlichte `routing-platform-debug-apk` (Artifact-ID `14363468060`, ZIP-Größe laut GitHub 53,326,687 Bytes). **Nicht** APK-Bytes und **kein** verifizierter SHA256. Kein Gerätetest.

## Umgesetztes, klein committedes Paket
1. `c77d7be057c1a817d4c420d8a1aace99e15a94b6`: Journey-Policy verwirft boolesche Cooldown-/Traffic-Zeitwerte, ungültige urgent-Flags und falsche Stop-Container; keine falsche Stau-Eskalation.
2. `f7146d7df419729e4653b064f0200a1bfa449c24`: Regressionen für manipulierte Zeitwerte und Kontexte.
3. `ae1ea5f9154e1864d00fb5b3bd5d5e9b6732062a`: Route-Client-Tests an das zuvor geänderte 16-MiB-Limit angepasst, großer valider Routen-JSON-Test ergänzt. Vorheriger 1-MiB-Test war nach Grenzänderung veraltet.
4. `4a50ecc8fef44f75e06d3f836fa2176863858f90`: Traffic-Attestierung erfordert echtes `True` statt truthy Strings/Zahlen.
5. `b68a9482ccb5d342807de7850bd0175f4a4f64b9`: Regressionen für gefälschte Traffic-Attestierung.
6. `d400a35f1c7fba930f15d36a2ece33fedac68174`: RouteEvidence-Zufahrt/Fahrtrichtung/Ausfahrt erfordern echtes `True`.
7. `33b55915833295bd2495bcfb3705617e96a721be`: Regressionen für gefälschte Route-Legalitätswerte.
8. `edb1ec24f81d0d73433e23b4803003e2de1a8fbc`: Via-POI-Adapter verwirft gefälschte Legalitätswerte vor Routing-IO.
9. `f30262bb6b6694bcf3cf752f96798e182f5e3a3d`: Tests sichern, dass bei solchen Eingaben Valhalla nicht aufgerufen wird.

## Verifikation und Blocker
- Vor dem Paket: alle fünf CI-Workflows auf Ausgangs-HEAD grün.
- Während des Pakets: GitHub-Workflows für Zwischencommits wurden gestartet; mehrere Zwischenläufe durch neue Pushes abgebrochen. **Für den letzten Code-Commit `f30262b` lag bei der ersten Abfrage noch kein Workflow-Ergebnis vor.** Morgenprüfung muss die CI des finalen Dokumentations-HEAD prüfen; keine unbewiesene grüne Aussage.
- Änderungen wurden per GitHub-Connector geschrieben. Keine lokale Ausführung von Android/Valhalla/Gradle oder Gerätetest in dieser Ausführung.
- Keine POI-Evidenzdaten, keine reale Valhalla-Graphattestierung, keine Android-UI-Freigabe. Die Journey-Policy bleibt advisory-only und fail-closed. Die frühere 16-MiB-Grenze ist nicht mit realen DACH-Routen benchmarked.
- PR #55 bleibt Draft. Kein main-Merge, Oracle-Deployment, Datenlöschung, Kosten oder Secrets.

## Priorisierte nächste acht Arbeitspakete
1. **P0 CI:** finalen HEAD prüfen; alle fünf Workflows und Android-APK-Artefakt, SHA256 aus echten Bytes ermitteln; bei Fehlern Logs auswerten und gezielt korrigieren.
2. **P0 Navigation:** reproduzierbare Offline-Tile-Import-/Persistenztests, Valhalla-Routing ohne Netz und WLAN-Handover mit Fallback.
3. **P0 Voice/Manöver:** Links/Rechts- und Prepare/Now-Timing-Regressionen mit versionierten anonymisierten Traces und tatsächlichen Manövercodes.
4. **P0 GPS/Release:** Jitter, Bearing, Re-Routing, Start/Ankunft; Pixel-10-Pro-Smoke-Plan, Fallback-APK und No-Go-Kriterien bis 20.11.
5. **P1 Backend-Sync:** SQLite-Outbox, atomare SHA256-Receipts, 409-Quarantäne, 429/503 Retry-After, Crash-Recovery und PostgreSQL-Parität.
6. **P1 Journey M1/M2:** unabhängig attestierter POI-Korpus und Valhalla-Graphidentität; echte Original-/via-POI-ETAs, Route-Order, 600/601s, Offline-Fail-Closed.
7. **P1 Journey M3/M4:** versionierte API, Consent-Feedback, Training-Center nur mit echten Daten, Security/CI-Integration; Android-Feature-Flag standardmäßig aus.
8. **P2 R2/R3:** UI und Traffic erst nach R1-Gates; keine erfundenen WC-/Stau-Verfügbarkeiten, keine ungeprüften KI-Routenänderungen.

## Rollback und Freigabe
Rollback: den zuletzt nachweislich grünen Commit `2d1653c2c0e49435ea9d1ec7e3c3547386768cf2` und dessen unverändertes Debug-Artefakt als Baseline behalten; keine Datenbankmigration in dieser Ausführung. Die neu hinzugefügten Journey-Validierungen können separat revertiert werden, falls CI eine Regression belegt. Physischer Pixel-10-Pro-Test offen. RC-Ziel Freitag 20.11.; testbarer Kandidat Samstag 21.11. nur nach Gate-Prüfung. Andernfalls No-Go mit Baseline-APK.
