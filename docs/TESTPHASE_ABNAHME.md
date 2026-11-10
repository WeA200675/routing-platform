# Testphase-Abnahme

## Automatische Prüfungen

    python3 -m unittest tools.test_provider_diagnostics -v
    python3 -m unittest tools.test_training_state_persistence -v
    python3 -m unittest tools.test_audit_integrity -v
    python3 tools/test_training_diagnostics_e2e.py
    python3 tools/training_api_healthcheck.py

## Android-CI

Die GitHub-Aktion Android CI führt Debug-Unit-Tests, Lint und Release-Kotlin-Kompilierung aus.

## VM-Betrieb

    sudo systemctl restart routing-training-api
    sudo systemctl status routing-training-api --no-pager
    curl -fsS http://127.0.0.1:8787/api/training/diagnostics | jq .

## Abnahmeszenarien

- Training starten und stoppen
- Providerereignis senden
- Netzwerk trennen
- Queue auf anwachsende Pending-Zahl prüfen
- Netzwerk wiederherstellen
- automatischen Upload prüfen
- Quarantäneereignis prüfen
- Quarantäne freigeben oder verwerfen
- API neu starten
- State-/Backup-Recovery prüfen
- Dashboard aktualisieren und echte Zeitstempel vergleichen
