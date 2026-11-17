# R3 – Intelligent Journey Companion MAX
Stand: 2026-11-17 · Arbeitsbranch: `fix/verified-training-pipeline`
Status: **Zielarchitektur / Umsetzungsauftrag, keine Produktfreigabe**. R1-Navigationsstabilität bleibt vorrangig. Die R2-Policy existiert bereits als `NavigationBreakAdvisor.kt`, aber die vollständige POI-/Traffic-/UI-Integration ist noch offen.

## Unveränderliche Produktregeln
1. Regulärer Vorschlag **nur bei >90 Minuten Fahrzeit seit letzter bestätigter Pause UND >60 km Reststrecke**. Stau darf die Fahrzeitschwelle vorzeitig übersteuern; optionales Gelegenheitsfenster nur per Opt-in.
2. **Maximal +600 Sekunden** zusätzliche Fahrzeit für die vollständige Route mit Zwischenziel im Vergleich zur ursprünglichen Route. Sekundenpräzision, nicht gerundete Minuten. Unbekannte oder negative Mehrzeit = nicht freigeben.
3. Nur legale, fahrtrichtungsrichtige Zu- und Abfahrt mit gesichertem Wiedereinstieg. Autobahn-Raststätten auf der Gegenseite ausschließen.
4. Kein automatischer Routenwechsel. Erst nach expliziter Bestätigung Zwischenziel setzen. Während der Fahrt nur minimal ablenkende Hinweise; Navigation hat Priorität.
5. WC-Status getrennt von Rastplatzkategorie: `verified`, `unknown`, `no`. Keine erfundene Verfügbarkeit, Belegung, Öffnungszeiten oder Verkehrslage.
6. Offline-Navigation muss ohne Internet, LLM und Pausenassistent funktionieren. Bei Ausfall bleibt die Originalroute unangetastet.
7. Standort- und Präferenzdaten lokal und datensparsam; Online-POI/Traffic nur mit Zustimmung, keine Rohspuren in GitHub.

## MAX-Architektur (separierte Verantwortlichkeiten)
- **Journey Planner:** beim Routenstart POI-Korridore, WC-Lücken und grobe Pausenfenster indexieren. Keine ungebremste Gesamtrouten-Neuberechnung.
- **Predictive Trigger:** 65–75 Minuten Hintergrundvorbereitung; regulär >90/>60; validierter Stau mit Route-Matching, Zeitstempel, Quelle und Prognosefenster kann früher auslösen.
- **POI Evidence Index:** offline OSM-Tags für Toiletten/Rastplätze/Services mit Karten- und POI-Version, Geometrie, legaler Zufahrt und evidenzbasiertem WC-Status. Optionaler Online-Fallback mit TTL und Provenienz.
- **Valhalla Via-POI Validator:** exakte Zeitdifferenz vollständiger Routen, Fahrtrichtung, Ausfahrt, Wiedereinstieg und ETA zum Haltepunkt; Revalidierung bei Reroute.
- **Traffic Opportunity Engine:** Szenarien Normalverkehr/Stau/Stauauflösung; Stauanfang entlang Route und erwartete Ankunft; Pause nur *vor* dem Stau empfehlen, sofern belegbar. Keine behauptete Zeitersparnis ohne Daten.
- **Candidate Ranker:** harte Gates zuerst; danach WC-Nachweis, Zeitumweg, Quellenqualität, Ausfahrtsvorlauf, Präferenz und Lage zum Stau. Höchstens drei begründete Alternativen.
- **Comfort Preference Model:** freiwillige, lokal gespeicherte Präferenzen; keine automatische Veränderung der harten Grenzen.
- **Suggestion State Machine:** idle/prefetch/eligible/offered/accepted/deferred/dismissed/cooldown; persistente Fahrtidentität, Pause und Routenwechsel; 30-Minuten-Cooldown und Deduplizierung.
- **Quality & Repair Loop:** datensparsame Qualitätsereignisse; Replay und Regression; nur reproduzierbare Fehler an R2-Engineering-Triage; keine autonomen Produktiv-Merges.

## UX-MAX
- Einfache Sprachansage: Grund, bestätigter WC-Status, Entfernung/Anfahrtszeit und zusätzlicher Umweg. Großes „Als Zwischenziel“, „Später“, „Für diese Fahrt aus“.
- Alternative Rastplätze nur in sicherer, fahrgeeigneter Ansicht; keine mehrstufigen Dialoge während der Fahrt.
- Hinweisvorlauf abhängig von Geschwindigkeit, Abstand zur Ausfahrt und aktuellem Navigationsmanöver; kein Vorschlag während kritischer Manöver.
- Bei verpasster Ausfahrt still neu berechnen; bei Stauauflösung Empfehlung diskret zurückziehen; nach Ablehnung kein sofortiger neuer Hinweis.
- Nutzer kann Schwellen und WC-Pflicht einstellen; Standardwerte bleiben 90/60/10.

## Ressourcen und Zuverlässigkeit
- Ereignisgesteuerte Updates statt dauerhafter LLM-Abfragen; harte CPU-/Akku-/Speicher-/Netzwerkbudgets.
- Cache-Schlüssel aus Route-ID, Map-/POI-Version, Traffic-TTL und Fahrtrichtung; bei Reroute, Zielwechsel oder Quellenalterung invalidieren.
- Begrenzte Parallelität, Timeout, Retry mit Backoff, Circuit Breaker; keine Blockierung des Haupt-Routing-Threads.
- Kein neues kostenpflichtiges Cloud-Angebot oder LLM ohne Freigabe.

## Große Umsetzungsgates
**G0 – Policy/Tests:** Sekundenpräzise Umweggrenze, validierte Trigger, persistente Zustände, WC-Evidenz und Unit-Tests.
**G1 – Offline-POI:** realer Offline-Index, Fahrtrichtungsfilter, WC-Evidenz und Fallbacktests.
**G2 – Routing:** Valhalla-via-POI-Differenz, echte ETA, legale Wiedereinfahrt, Reroute/Offline-Regressionen.
**G3 – Traffic:** Route-matched, quellengebundene Stauereignisse, rechtzeitige Vorwarnung, stale-data-Fallback.
**G4 – Android-UX:** Voice/UI, Bestätigung, Dismissal, persistenter Cooldown, minimale Ablenkung.
**G5 – Training:** Opt-in-Feedback, Qualitätsmetriken, Regressionsevidenz, R2-Triage-Integration.
**G6 – Release:** Android/Core/Backend CI grün, echte Offline-Fahrt, reproduzierbare APK mit SHA-256, Rollback. Feature-Flag erst dann freigeben.

## Testmatrix (Mindestumfang)
- Exakte Schwellen 90/60 und >90/>60; 600 vs. 601 Sekunden; NaN/Null/negative ETA.
- Vor-Stau-POI vs. Stau-vor-POI; stale/fehlende Stauquelle; Stauauflösung.
- Gegenfahrbahn, illegale Ausfahrt, fehlende Wiedereinfahrt, verpasste Ausfahrt.
- Keine POIs, WC unbekannt, Datenquelle veraltet, offline, LLM-Ausfall.
- GPS-Jitter, Reroute, Prozesskill, Neustart, Zeitumstellung, wiederholte Meldungen.
- CPU-/Akku-Budget, Test der Nichtbeeinflussung von Manöver- und Voice-Logik.
- Kein automatisches Zwischenziel; vollständige Testbelege für alle Gates.

## Autonome Entwicklungsregeln
Arbeiten in kleinen testbaren Commits ausschließlich auf Entwicklungsbranch; unabhängige Tests vor Abschluss. R1-M1-Navigationsblocker und R1-M3-Datenintegrität haben Vorrang. Keine produktiven Deployments, main-Merges oder APK-Veröffentlichungen ohne explizite Freigabe. Bei fehlender Datenquelle oder Gerät ehrlich blockiert melden und unabhängige Arbeit fortsetzen.
