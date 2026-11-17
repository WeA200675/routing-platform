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

## R3.1 – Digitaler Reisezwilling, WC-Sofortmodus und nachweisbare Optimierung
**Mehrwert statt Feature-Aufblähung:** Jede neue Funktion muss eine messbare Qualitätsverbesserung liefern und darf die Basisnavigation nicht destabilisieren.

### Digitaler Reisezwilling (lokal, begrenzt)
- Route in Abschnitte mit Zeit, Länge, Fahrtrichtung, Ausfahrten, POI-Evidenz und Verkehrslage zerlegen.
- Für die nächsten relevanten Pausenfenster Szenarien normaler Verkehr, Stau, Stauauflösung und verpasste Ausfahrt berechnen; kein dauerhaftes LLM-Sampling und keine erfundenen Wahrscheinlichkeiten.
- „Letzte gute Gelegenheit“: explizit warnen, wenn nach dem letzten verifizierten WC ein längerer Abschnitt ohne belegte Alternativen folgt. Schwelle konfigurierbar, keine Panikmeldungen.
- Bei unbekannter POI-Abdeckung keine Behauptung „kein WC auf den nächsten X km“; stattdessen „keine weiteren WCs in den vorhandenen Daten bestätigt“.
- Alternative Routen dürfen die normale Navigation nicht unbemerkt verändern.

### WC-Sofortmodus (bewusste Nutzeranforderung)
- Große, schnell erreichbare Aktion „WC jetzt“ und optional verlässlicher Sprachbefehl.
- Übersteuert die regulären >90-Minuten- und >60-km-Auslöser, nicht jedoch legale Zufahrt, bestätigten WC-Status und die Nutzerentscheidung.
- Die automatische +600-Sekunden-Grenze bleibt Standard. Bei dringender expliziter Suche darf die UI zusätzlich längere Umwege **klar gekennzeichnet** anbieten, aber nur nach gesonderter Bestätigung; niemals stillschweigend die Grenze verändern.
- Kein verifizierter WC-Treffer: verfügbare Alternativen klar mit „WC unbekannt“ kennzeichnen und ausdrücklich keine falsche Zusicherung machen.

### Stau-Pause vs. Weiterfahren
- Drei Varianten vergleichen: unverändert weiterfahren, bestätigter Halt vor Stau, alternative Route (falls Routingdaten vorhanden).
- Zusätzliche Fahrzeit, Aufenthaltszeit und prognostizierte Ankunftszeit getrennt ausweisen; keine angebliche „Zeitersparnis“ allein durch Pausieren.
- Für Verkehrsdaten Provenienz, Alter, Fahrtrichtung, Route-Matching und Prognoseunsicherheit berücksichtigen.
- Keine Stau-optimierte Empfehlung, wenn das Risiko einer verpassten Ausfahrt oder einer fehlerhaften Verkehrslage zu hoch ist.

### Priorisierung und Beweisbarkeit
- Harte Sicherheitsschranken vor Ranking und Personalisierung.
- Für jeden Vorschlag lokal speichern: Policy-Version, Route-/Map-/POI-Version, Triggergrund, geprüfte Kandidatenanzahl, Ablehnungsgründe, Umwegsekunden, WC-Evidenz, Nutzerentscheidung – ohne unnötige Rohkoordinaten.
- Vor Aktivierung Replay-Testfälle für Stau, lange WC-Lücke, Sofortmodus, offline, Prozessneustart, falschseitige Raststätte, 600/601 Sekunden, unbekannte Evidenz und App-Absturz.
- Erfolgsmaße: falsche WC-Zusagen = 0, illegale Zufahrten = 0, Vorschläge >600 Sekunden ohne explizite Ausnahme = 0, automatisch geänderte Zwischenziele = 0, keine Regression bei Sprachmanövern, CPU-/Akku-Budget.
- Verbesserungen nur aus realen Daten oder reproduzierbaren Tests ableiten; LLM darf weder Straßen- noch WC-Evidenz halluzinieren.

### Umsetzungsgrenze
Dieser Abschnitt ist eine **Spezifikation**. Die neue Journey-Twin-/WC-Sofortmodus-Logik, Routing-Integration und Android-UI sind noch nicht implementiert oder verifiziert. Weiterhin G0–G6 in Reihenfolge und R1 vor R3.
