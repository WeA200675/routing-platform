# Nachtentwicklung 2026-11-16 – geprüfter Arbeitsstand

## Scope und Sicherheitsgrenzen

Branch: `fix/verified-training-pipeline`; PR: https://github.com/WeA200675/routing-platform/pull/55 (Draft). Keine Änderungen an `main`, kein Merge, keine Oracle-VM- oder Produktionsänderung. Eine einzelne automatisierte Ausführung ist **kein** durchgängiger Hintergrundworker.

## Nachweisbare Änderungen

- `2a52bf171bacd4219bcbcf4c413894911c8919c1`: SQLite-Verbindungen in allen Pfaden explizit schließen.
- `47d579b3012b94c297cf5643cb95c3c1a861ce73`: Regressionstest für Verbindungsfreigabe bei Erfolg und Exception.
- `cb4b283d440857baa4f74a1a9681a61ce7940945`: Begrenzte Wiederholung abgelaufener Replay-Leases (`max_attempts=5`), anschließend Zustand `blocked_no_runner` und Fehler `retry_exhausted`, Payload bleibt erhalten.
- `de53065262d459ed6e6470850bea0f9f89bdd9bc`, `11d4c0d87edc8a9d7dca248aee9f2e31b9147b78`: Explizites Requeue setzt nur bei erschöpftem Retry-Budget den Versuchszähler zurück; normale Requeues behalten die Historie.
- `fdf150f666b3adc01c6be2c409db59ccda1549dc`: Tests für Retry-Erschöpfung, Wiederanlauf und Vermeidung von Queue-Starvation.

CI zum Zeitpunkt der Erstellung: Die Workflows des jüngsten Commits waren noch nicht abgeschlossen; **keine Behauptung über bestandene Tests**. Lokal konnte das Repository wegen fehlender DNS-Auflösung für github.com nicht geklont und getestet werden. Die GitHub-Connector-Schreiboperationen waren erfolgreich.

## Acht Arbeitspakete – tatsächlicher Status

1. **CI/Regressionen:** PR und Testdateien geprüft; neue Tests ergänzt; aktuelle CI-Ergebnisse ausstehend.
2. **SQLite-Queue-Härtung:** Verbindungsschließung und begrenzte Lease-Retries implementiert; noch nicht durch jüngste CI bestätigt.
3. **PostgreSQL:** **Nicht implementiert.** Vor Einführung: Transaktions-/Lease-Parität, Retry-Limits, Migrations- und Rückfallstrategie, Integrationstest mit echtem PostgreSQL.
4. **Offline/Idempotenz:** Bestehende atomare `submit_batch`-Logik geprüft; keine neue Android-Synchronisierung implementiert.
5. **API-Sicherheit:** Offene HTTP-POST-Endpunkte und `Access-Control-Allow-Origin: *` erkannt; Authentisierung, Rate-Limits und Reverse-Proxy-Grenzen vor öffentlicher Exposition notwendig. Keine Produktionskonfiguration geändert.
6. **Valhalla/Links-Rechts:** `backend/valhalla_replay.py` und vorhandene Richtungstests geprüft; keine Live-Fahrdaten oder unabhängige Map-/Binary-Attestierung vorhanden.
7. **KI-Nachweise:** `complete()` bleibt absichtlich Fail-Closed ohne vertrauenswürdigen Attestor. Nicht gemessene Qualitätskennzahlen bleiben `null`; keine KI-Verbesserung behauptet.
8. **Tests/Dokumentation:** Diese Dokumentation und neue Unit-Tests erstellt; Android- und Backend-End-to-End-Abnahme ausstehend.

## Nächster gleich großer Entwicklungsauftrag

1. GitHub-CI für jüngsten Branch-Head auswerten und jeden fehlgeschlagenen Test mit reproduzierbarem Fix beheben.
2. SQLite-Queue unter Konkurrenz, Prozessabbruch, Lock-Timeout und vollem Datenträger testen; Retry-Budget und manuelles Requeue auditieren.
3. PostgreSQL-Jobstore als **explizites Opt-in** mit identischem Contract, Transaktionen, `FOR UPDATE SKIP LOCKED`, Retry-Budget und Fail-Closed-`complete()` bauen.
4. SQLite/PostgreSQL-Contracttests und eine sichere, idempotente Migration mit Backup, Dry-Run und Rollback implementieren.
5. Authentisierte Ingest-API mit sicheren Defaults, kontrollierter CORS-Policy, Request-Validierung und Reverse-Proxy-Tests implementieren.
6. Android-Offline-Outbox mit stabilen Event-IDs, atomarem Batch-Ack, Retry/Backoff und Absturz-Recovery end-to-end testen.
7. Valhalla-Links/Rechts- und Sprachansage-Regressionen anhand versionierter, datenschutzbereinigter Fahrtraces reproduzieren; echte Map-/Binary-Provenienz vor Promotion prüfen.
8. Einen isolierten Replay-Attestor und nachvollziehbare Qualitäts-Baselines integrieren; PostgreSQL/API/Android-Systemtests, Sicherheitsgate und dokumentierte Freigabeentscheidung durchführen.

**Abnahme:** Keine erfolgreiche Navigation oder KI-Verbesserung ohne reproduzierbare Evidenz; kein automatischer Merge/Deploy. Bei fehlender Infrastruktur unverifizierte Arbeiten offen lassen.
