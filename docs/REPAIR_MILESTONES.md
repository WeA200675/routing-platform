# Gesamthafte Reparaturplanung

Branch: `recovery/startup-crash-refactor`
Zielbasis: `g620-integration-local`
Stand: 2026-11-14

## Meilenstein 1 — Startstabilität
Status: **umgesetzt**

- MapLibre-Initialisierung isoliert.
- Sichtbarer Degraded-Modus bei Kartenfehlern.
- JNI-Laden darf den Launcher nicht mehr beenden.
- Native Navigation liefert bei Ausfall einen validierten Diagnose-Snapshot.

Commits: `9b646de`, `0576805`, `f0f8b40`

## Meilenstein 2 — Route-Bootstrap und Routenwechsel
Status: **umgesetzt**

- Bootstrap-Fallback ist unabhängig von der Native-Brücke.
- Route-Installation und Ersatzrouten sind bei Native-Ausfall geschützt.
- Rekursive Fehlerpfade wurden entfernt.

Commit: `c7f8bb2`

## Meilenstein 3 — KI-Zulassung und Wahrheitsgrenze
Status: **teilweise umgesetzt**

- Zentrale `RuntimeCapabilityMatrix` ergänzt.
- Gemeinsames `SocialAiRuntimeAdmission` ergänzt.
- Read-only Admission-Probe im Produkt-Runtime verfügbar.
- Unit-Tests für Capability-Sicherheitsregeln ergänzt.
- Das lokale Modell und die Native-Laufzeit werden nur nach Integritätsprüfung zugelassen.
- Die vorhandene `social_ai_llama_jni.cpp`-Implementierung ist aktuell fail-closed und liefert keine echte Inferenz.
- Die App darf diese Laufzeit daher nicht als aktiv ausweisen.
- Nächster Code-Schritt: ein einheitliches Admission-Ergebnis für Produktlauf und Trainingsworker.

## Meilenstein 4 — Eager-Startup und Lebenszyklus
Status: **in Bearbeitung**

- Alle während Compose erzeugten Runtime-Komponenten werden auf mögliche Seiteneffekte geprüft.
- Netzwerk-, Sensor-, Trainings- und Upload-Komponenten dürfen den Launcher nicht blockieren.
- Optionales Training darf ausschließlich über WorkManager bzw. explizite Aktionen starten.

## Meilenstein 5 — Runtime-Navigation
Status: **offen**

- echte Positionsversorgung,
- Fortschrittszyklus,
- Recovery-Aufträge,
- sichere Neuberechnung,
- Karten-/Sprachführungs-Gate.

## Meilenstein 6 — POI, Via-Punkte und Energieplanung
Status: **teilweise vorhanden, Integration offen**

- Parser, Cache und Ranking sind vorhanden.
- UI-Übernahme und echte Routenintegration müssen auf dem Gerät geprüft werden.
- Tank-/Ladeplanung benötigt reale Provider und End-to-End-Tests.

## Meilenstein 7 — Trainingspipeline
Status: **teilweise vorhanden, Integration offen**

- FIFO, Replay, Regression und API-Verträge existieren.
- Navigationsereignisse, Upload und Cloud-Normalisierung müssen im realen Lauf verifiziert werden.

## Meilenstein 8 — Verifikation und Auslieferung
Status: **offen**

- GitHub-Actions-Build,
- Unit-, Instrumentierungs- und Offline-Tests,
- Starttest auf echtem Gerät,
- Signatur- und SHA-256-Prüfung,
- erst danach Release-APK.

## Abschlusskriterium

Der Recovery-Branch gilt erst als abgeschlossen, wenn alle Meilensteine entweder mit einem erfolgreichen Testnachweis abgeschlossen oder als klar blockiert dokumentiert sind. Eine erfolgreiche Kompilierung allein zählt nicht als Funktionsnachweis.
