# Gesamtintegration Routing-App / KI / PostgreSQL – Ausführungsauftrag

Stand: 2026-11-17. Branch: `fix/verified-training-pipeline`, PR #55 (Draft).
Ausgangsbasis: Commit `e54b5e860d305640725f14947178aa2c58dc0ef8` mit vier erfolgreichen GitHub-Workflows (Android CI, Android/core verification, Core CI, Training backend safety checks). Dies beweist **nicht** die End-to-End-Funktion.

## Ziel und Freigaberegel
Eine aufgezeichnete Fahrt erzeugt stabile Event-IDs; die Android-Outbox speichert offline; die API nimmt idempotente Batches an; PostgreSQL hält Jobs transaktional; ein isolierter Valhalla-Replay-Worker erzeugt prüfbare Artefakte; ein unabhängiger Attestor entscheidet über Regressionen; die KI darf nur nach bestandenem Sicherheitsgate Änderungen vorschlagen. Live-Navigation darf bei jedem Backendfehler unverändert weiterlaufen.

Keine automatischen Merges, Deployments, Oracle-Produktivänderungen, neuen Kosten oder unautorisierten Installationen. Feature-Flags standardmäßig aus. Keine behaupteten Verbesserungen ohne reproduzierbare Messung.

## Acht große, ausführbare Pakete

### 1. Contract und Baseline einfrieren
- Repository-Head, PR, CI, API-Schemas, Android-Eventtypen, Queue-State-Machine und Valhalla-Version erfassen.
- Versionierte JSON-Fixtures: Online, Offline, Doppelsendung, Konflikt, falsches Manöver, verspätete Ansage, GPS-Jitter.
- Baseline-Metriken nur aus echten Tests; unbekannte Werte bleiben null.
- Gate: reproduzierbare Contracttests und dokumentierte Ist-Schnittstellen.

### 2. PostgreSQL-Adapter mit SQLite-Parität
- Expliziter Opt-in-Backendselector; kein stiller Wechsel der produktiven DB.
- Transaktionen, eindeutige Event-IDs, Payload-Digest, Kapazitätsgrenzen, `FOR UPDATE SKIP LOCKED`, Lease-Token, Ablauf, Retry-Budget, Dead-letter/blocked.
- SQL-Parameterbindung, Verbindungs-Limits, kurze Timeouts; Schema-Migrationen versionieren.
- Gate: derselbe Contracttest-Satz auf SQLite und realem PostgreSQL, einschließlich Parallel-Claims.

### 3. Sichere Migration und Wiederherstellung
- Dry-run, Snapshot/Backup, Prüfsummen, Zähl- und Digestvergleich, idempotenter Import, Cutover nur per explizitem Flag.
- Rollback über unveränderte Quelldaten; niemals unbestätigte Jobs löschen.
- Ausfälle testen: Prozessabbruch, DB-Verlust, Locking, Disk-full, Restart.
- Gate: Wiederanlauf ohne verlorene oder doppelt verarbeitete Events.

### 4. API-Härtung und Ingest
- Authentifizierte schreibende Endpunkte (Token/Proxy), lokale Readiness und Health getrennt.
- CORS-Allowlist statt Wildcard für schreibende Schnittstellen, Größen-/Ratenlimits, saubere 4xx/5xx, keine Secrets in Logs.
- Atomare Batch-Acks mit stabilen IDs und Konflikterkennung; 503/429 mit Backoff.
- Gate: Negativtests für fehlende Auth, Replay-Angriff, Payload-Konflikt und Überlast.

### 5. Android-Outbox und Offline-Fallback
- Lokale persistente Queue, stabile Event-IDs, Wiederholung mit exponentiellem Backoff/Jitter.
- Ack nur nach bestätigtem Commit auf dem Server; Absturz vor/nach Ack und Netzwechsel testen.
- Begrenzter Speicher mit Warnung und sicherer Retention, keine stille Löschung unbestätigter Daten.
- Gate: Flugmodus -> Fahrt -> Neustart -> Wiederverbindung -> exakt einmal logisch angenommen.

### 6. Valhalla, Manöver und Voice
- Versionierte, datenschutzbereinigte Traces für Links/Rechts, Autobahnausfahrten, Ansage-Timing und GPS-Jitter.
- Deterministischer Replay-Runner mit Map-/Binary-/Fixture-Digest; Unbekanntes fail-closed.
- Routing und Ansage separat testen; nie ungeprüfte Lernkorrekturen live aktivieren.
- Gate: alle Referenztraces bestanden und belegte Richtungs-/Timing-Metriken.

### 7. KI-Trainingskette mit vertrauenswürdiger Evidenz
- Worker isoliert, begrenzte Ressourcen, idempotente Jobs, leases, retry exhaustion und Audit-Log.
- Unabhängiger Attestor prüft signierte/gebundene Replay-Artefakte, Baseline und Regressionen.
- LLM ist optionaler Vorschlagsgenerator; deterministische Regeln und Offline-Navigation bleiben Fallback.
- Gate: kein `passed` oder Promotion ohne unabhängige Evidenz; absichtliche Fehler werden blockiert.

### 8. End-to-End-Abnahme und Release-Kandidat
- CI-Matrix: Android, Backend, PostgreSQL-Service, Replay, Security, Recovery, Performance.
- E2E-Kette mit Testfahrt inklusive Offline, Wiederverbindung, DB-Ausfall und Wiederanlauf.
- Dashboards nur mit tatsächlich erfassten Daten, Status `unknown` bei fehlender Messung.
- Draft-PR mit Testmatrix, Risiken, Rollback, offenen Punkten und Freigabeentscheidung.
- Gate: erst bei allen bestandenen Pflichtprüfungen Review zur Freigabe vorschlagen; kein automatischer Merge/Deploy.

## Reihenfolge und parallele Stränge
Zuerst Paket 1. Danach 2 und 4 parallel; 3 hängt von 2 ab, 5 von 4, 6 kann parallel erfolgen. Paket 7 benötigt 2/4/6; Paket 8 benötigt alle. Bei fehlendem PostgreSQL-Runner: Adapter und Tests vorbereiten, PostgreSQL-Gate als **blocked** markieren, SQLite-Fallback weiter prüfen. Bei fehlenden Fahrtraces: synthetische Tests nur als Vorprüfung ausweisen.

## Nacht- und Morgenübergabe
Jede Ausführung: Ist-Stand -> kleinste sichere Änderung -> Tests/CI -> Commit -> Ergebnis/Evidenz -> nächstes Paket. Um 04:45 Uhr nur verifizierte Resultate berichten; ungetestete Arbeit separat ausweisen. Ein zeitgesteuerter Lauf ist kein dauerhaft aktiver VM-Worker.

## Nächste konkrete Schritte
1. Aktuellen Branch-Head und CI nach diesem Dokumentationscommit prüfen.
2. API-Schreibpfade und bestehende PostgreSQL-/Compose-Konfiguration inspizieren.
3. Contracttests für `submit`, `submit_batch`, `claim`, `block`, `requeue_blocked`, `status`, `counts` extrahieren.
4. PostgreSQL-Adapter ausschließlich opt-in implementieren und gegen echten PostgreSQL-Dienst testen.
5. Nur mit nachgewiesenen Gates in nachgelagerte Pakete gehen.
