# Recovery R2 – Autonomous Engineering & Intelligence
Stand: 2026-11-17. Arbeitsbranch: `fix/verified-training-pipeline`. R1 bleibt priorisiert.

## Ziel und Sicherheitsgrenzen
Fehler aus Navigation, CI und Backend reproduzierbar erfassen, priorisieren, reparieren und verifizieren. Automatische Code-Commits **nur** auf dem Entwicklungsbranch. Kein autonomer Merge nach main, kein produktives Oracle-Deployment, keine APK-Veröffentlichung. Navigations-KI bleibt advisory-only. Fehlende Daten gelten nicht als Erfolg. Keine personenbezogenen GPS-Spuren oder Secrets in GitHub-Issues/Logs.

## R2-Gates
- **R2.0 Incident Triage:** Stabile Fingerprints, Severity, Deduplizierung, Eingabevalidierung und Tests. **Implementiert als isoliertes Modul; CI ausstehend.**
- **R2.1 Telemetrie-Adapter:** Opt-in, lokale Datenminimierung, PII-/Token-Redaktion, Rate Limits, Quarantäne für unsichere Ereignisse. Ausstehend.
- **R2.2 Reproduktionslabor:** Deterministische Replay-Fixtures mit Map-/App-Version, GPS/Voice/Manöver-Gates; unabhängige Replay-Attestierung vor Promotion. Ausstehend.
- **R2.3 Reparatur-Orchestrator:** Persistente Aufgaben, Lease, Retry mit Obergrenze, Dead-letter-Queue, getrennte Analyse-/Patch-/Review-Agenten, Zeit-/CPU-/Kostenbudgets. Ausstehend.
- **R2.4 GitHub-Integration:** Deduplizierte Issues, separate Fix-Branches, kleinste Berechtigungen, CI und Reviewer; keine untrusted PR-Ausführung mit Schreib-Token. Ausstehend.
- **R2.5 Verbesserungs-Engine:** Messbare Performance-, UX-, Akku- und Offline-Ziele; A/B-Vergleich mit Baseline, keine erfundenen Live-Metriken. Ausstehend.
- **R2.6 Dashboard & Release-Gates:** Agentenstatus, Warteschlange, Ursache, Tests, Rückfallpunkt, nachvollziehbare APK; reale Gerätetests als eigenes Gate. Ausstehend.

## Reihenfolge
1. R1-M1 Navigationsblocker und R1-M3 Datenintegrität bleiben vor neuen Features.
2. R2.0/1 parallel als isolierte, rückwärtskompatible Module.
3. R2.2 vor R2.3: keine automatisierte Reparatur ohne reproduzierbare Evidenz.
4. R2.4 nach Security-Review, getrennte Berechtigungen für Lesen, Issue-Schreiben und Branch-Commit.
5. R2.5/6 nach reproduzierbaren Baselines.

## Arbeitsmodus
- Priorität: Crash/Sicherheits-/Navigationsblocker > Links/Rechts/Offline/Voice > Synchronisierung > Performance > Features.
- Backlog erhält Fingerprint, Severity, betroffene Version, Evidenzstatus, Teststatus und nächsten Schritt.
- Worker darf einen fehlerhaften Patch nicht wiederholt endlos anwenden. Bei Testfehler: stoppen, Befund dokumentieren, nächsten unabhängigen Task bearbeiten.
- Ohne GitHub-Zugriff, Netzwerk, Gerät oder freie Rechenressourcen: kein stilles Erfolgssignal, sondern nachvollziehbarer Blocker und Fallback.
- Budget: standardmäßig keine kostenpflichtigen Modelle oder Cloud-Ressourcen aktivieren.
- Nutzerbericht: R1/R2 getrennt, konkrete Commits, tatsächliche CI-Resultate, offene Risiken.

## Erste Umsetzung
- `backend/engineering_triage.py`: deterministische Fehleridentität, Prioritäten, Datenminimierung und Deduplizierung.
- `tools/test_engineering_triage.py`: Regressionstests.
- `.github/workflows/training-backend-safety.yml`: Python-Syntaxprüfung und Unit-Test-Aufruf.

**Wichtig:** Der neue Triage-Baustein ist noch kein dauerhaft laufender Agent und empfängt noch keine Telemetrie aus der Android-App. Die weiteren Gates sind bewusst offen.
