# R2 – Vorausschauender WC- und Rastplatzassistent

Stand: 2026-11-17. Additive Funktion; keine Freigabe der R1-Navigation.

## Verbindliches Verhalten
- Nach **mehr als 90 Minuten Fahrt seit Start/letzter bestätigter Pause** und **mehr als 60 km verbleibender Route** WC oder Rastplatz anbieten.
- Umweg höchstens **10 Minuten zusätzlicher Fahrzeit**, als Differenz der vom Routingmotor berechneten Gesamtfahrzeit mit und ohne Zwischenziel; kein Luftlinien-Schätzwert.
- **Vor Stau:** Bei verlässlichem Stauereignis auf der aktiven Route frühzeitig geeignete Halte **vor** dem Stau suchen; initiales Suchfenster 30 Minuten bis zum Stau, einstellbar. Stau darf die 90-Minuten-Bedingung übersteuern, sofern noch mehr als 60 km Reststrecke vorhanden sind.
- Keine automatische Routenänderung. Nur nach expliziter Bestätigung als Zwischenziel übernehmen; Ablehnen/Später anbieten.
- Bei unklarer Umwegzeit, unzugänglicher Fahrtrichtung, fehlenden Offline-POIs oder nicht verifizierter Stauquelle: keine ungesicherte Empfehlung; vorhandene Route unverändert.
- Doppelte Empfehlungen vermeiden: 30 Minuten Cooldown, Reset nach bestätigter Pause oder neuem Ziel; kritische Verkehrslage darf optional separat behandelt werden.
- Keine WC-Verfügbarkeits- oder Öffnungszeitenbehauptung ohne verifizierte Quelle. Park-/Rastplätze können kein öffentliches WC haben; Kategorien unterscheiden.
- Datenschutz: lokale Entscheidung ohne Hochladen der exakten Position; Online-Stau nur bei Zustimmung.

## Technische Umsetzung und Gates
1. **Policy-Kern:** `NavigationBreakAdvisor.kt` mit deterministischen Schwellen, Stau-Vorwarnung, 10-Minuten-Grenze, sicherem Ausfall und Cooldown. **Eingecheckt, noch nicht in Fahrt-UI eingebunden.**
2. **POI-Suche:** Offline-OSM-POI-Index für `amenity=toilets`, `highway=rest_area`, `highway=services`; echte Fahrtrichtung/Zufahrten prüfen. Optional Online-Fallback nur mit Consent.
3. **Routingvergleich:** Für Kandidaten Route über Zwischenziel gegen ursprüngliche Route rechnen, Mehrzeit und Ankunftsreihenfolge validieren; maximal 10 Minuten.
4. **Stauprognose:** Nur route-matched Verkehrsdaten mit Quelle, Alter, Startposition und Zeitfenster; Suche rechtzeitig vor dem Stau.
5. **UI:** Nicht störende Karte „Pause vorschlagen“ mit Name, WC-Status, Zusatzminuten, Ankunft, „Als Zwischenziel“, „Später“, „Nicht mehr fragen“.
6. **Tests:** 90/60 exakt => keine normale Empfehlung; 91/61 => möglich; 10 Minuten erlaubt, 11 abgelehnt; vor Stau auch unter 90 Minuten; kein POI/keine ETA => kein Vorschlag; falsche Fahrtrichtung => abgelehnt; 30-Minuten-Cooldown; Offline-/Neustart-/Gerätetest.
7. **Rollout:** Feature-Flag standardmäßig deaktiviert, bis POI-Suche, Route-ETA, UI und Regressionen durchgängig funktionieren.

## Noch offen
Keine verifizierte Offline-POI-Datenquelle, kein integrierter Verkehrsdaten-Adapter und keine angeschlossene Android-UI in diesem Commit. Ein Policy-Kern allein bedeutet ausdrücklich nicht, dass die Funktion schon auf dem Handy arbeitet.

## R2.7 – Predictive Comfort Assistant, Ausbaustufe 2 (Best-Case)
**Bestehende Schwellen bleiben Defaults** (>90 Minuten, >60 km, maximal +10 Minuten), aber Suche und Hinweis sind getrennt: ab Minute 65–75 Kandidaten im Hintergrund vorbereiten, erst bei erfülltem Trigger empfehlen. Das vermeidet verpasste Ausfahrten und hält die UI ruhig.

### Mehrstufige Entscheidungsstrategie
1. **Harte Ausschlussregeln:** bestätigte Route, legaler Abzweig und Wiedereinstieg, aktuelle Fahrtrichtung, Umweg <=10 Minuten nach vollständiger Route-via-POI-Berechnung, kein Rückwärtsfahren auf Autobahn, sichere Reststrecke. Unbekannte Umwegzeit = kein Kandidat.
2. **Stau-Auslöser:** nur wenn Quelle/Alter/Position des Staus belastbar sind und Rastplatz vor dem erwarteten Stauanfang liegt; dynamisches Vorwarnfenster abhängig von Anfahrt, Ausfahrthinweisen und Puffer. Falls der Stau vor dem Haltepunkt beginnt: Kandidat verwerfen. Bei fehlender Live-Verkehrsquelle weiterhin normale Zeitregel.
3. **Gelegenheitsfenster (optional, standardmäßig aus):** wenn die nächsten geeigneten WC-Punkte weit auseinanderliegen, einen frühen, dezenten Hinweis ermöglichen; keine automatische Zieländerung.
4. **Ranking statt nur kürzester Umweg:** WC-Nachweis, sichere Erreichbarkeit, Stauposition, verifizierte Öffnungszeiten, Route-via-POI-Fahrzeit, Evidenzalter und Abfahrtsvorlauf. Bei unklarer WC-Ausstattung klar als „Rastplatz, WC unbekannt“ kennzeichnen.
5. **Bis zu drei Alternativen:** nur wirklich geeignete Kandidaten, niemals die 10-Minuten-Grenze aufweichen. Bei 0 Treffern ehrlich „keine verifizierte Option“ statt halluzinierter Empfehlung.

### Bedienung während der Fahrt
- Ruhige Sprachansage einmalig mit großem, fahrgeeignetem Bestätigungsfeld; kein Scrollen, keine komplexe Auswahl während der Fahrt.
- Buttons: „Als Zwischenziel“, „Später“, „Nicht mehr fragen“; optionale Sprachbestätigung nur bei hinreichend sicherer Erkennung. Kein unbestätigtes Umschalten der Route.
- Cooldown 30 Minuten für dieselbe Empfehlung, Deduplizierung nach POI-ID und Routenabschnitt. Nach explizitem Ablehnen keine sofortige neue Empfehlung derselben Kategorie.
- Fahrzeit aus monotoner Zeitbasis für aktive Fahrt ermitteln; Pausen nur anhand expliziter Bestätigung oder verlässlich erkanntem längeren Stillstand zurücksetzen, kein Reset durch kurze Ampelstopps.
- Nutzeroptionen: Schwellen, max. Umweg, WC-Pflicht, Sprachhinweise, Gelegenheitsfenster, Stau-Warnzeit; Defaultwerte bleiben wie gewünscht.

### Daten- und Modellarchitektur
- **Deterministische Policy ist maßgeblich**; LLM darf nur natürliche Formulierungen und begründete Alternativen anbieten, nie GPS-/Stau-/WC-Daten erfinden oder Sicherheitsgrenzen überschreiben.
- Offline-first: lokaler OSM-POI-Index mit Datenversionskennung und Offline-Routing über Valhalla. Online-POI/Traffic optional mit Zustimmung, Cache, TTL und Quellenstatus.
- Zeitabhängiger ETA-Vergleich mit Verkehrsdaten, soweit verfügbar; bei unsicherer Verkehrsprognose konservativ ohne behauptete Minutenersparnis.
- Cache invalidieren bei Reroute, Zielwechsel, starkem Standortversatz, POI-Datenupdate oder Stauänderung. Kein dauerndes Neurouting; begrenzte Rechenzeit und Akku-Budget.
- Debug-/Trainingsevents datensparsam: Triggergrund, Kandidatenanzahl, Ablehnungsgründe, berechneter Umweg, Bestätigungsstatus; keine Roh-GPS-Spuren oder Secrets ungefragt übertragen.

### Zusätzliche Abnahmetests
- 65–75 Minuten: nur Hintergrundsuche, keine Meldung; >90 und >60: Angebot; bei 90/60 exakt keine reguläre Meldung.
- 10 Minuten Umweg zulässig; 10 Minuten + 1 Sekunde abgelehnt; Mehrzeit über komplette Strecke vergleichen.
- Stau vor Rastplatz => kein Stau-Pausenvorschlag; Stau nach Rastplatz => Vorwarnung möglich; Stau verschwindet => Empfehlung aktualisieren.
- Autobahn-Rastplatz falsche Fahrtrichtung / keine legale Wiedereinfahrt => Ausschluss.
- WC unbekannt => keine Behauptung; Offline-POIs fehlen => normale Navigation unverändert.
- Kein doppeltes Angebot nach Reroute, Neustart, Sprachwiederholung; Cooldown über Sitzungszustand.
- GPS-Jitter, falsche/alte Verkehrsdaten, Null-/NaN-ETA, Zeitumstellung, Hintergrundwechsel und Speicherknappheit.
- Gerätetest bei realer Fahrt und dokumentierter Nutzerbestätigung als Release-Gate.

**Status:** Konzeption ergänzt. Keine Behauptung einer bereits funktionsfähigen POI-, Traffic- oder UI-Anbindung.

## R2.8 – Predictive Journey Intelligence (PJI)
Ziel: nicht nur einzelne Rastplätze finden, sondern Pausen als Teil der gesamten Fahrt robust, vorausschauend und mit nachvollziehbarer Evidenz planen.

### Zwei Ebenen statt Dauer-Neuberechnung
- **Journey Planner** am Routenstart: grobe POI-Korridore, erwartete Fahrzeit, lange Abschnitte ohne WC und mögliche Pausenfenster indexieren. Kein permanentes Routing aller POIs.
- **Local Decision Engine** unterwegs: nur nächsten relevanten Korridor bei Ereignissen (Fahrtzeit, Reroute, Verkehr, verpasste Ausfahrt) nachberechnen. Strikte CPU-/Akku-/Zeitbudgets; Cache mit Route-ID, Map-Version und Ablaufzeit.
- **Vorbereitung vs. Empfehlung:** Vorladen ab ca. 65–75 Minuten (konfigurierbar), Anzeige regulär erst bei >90 Minuten UND >60 km. Stau darf die Fahrzeitschwelle übersteuern; harte 10-Minuten-Grenze und sichere Zufahrt gelten immer.

### Stau und Haltepunkte als zeitabhängiges Problem
- ETA zur Stauanfangsstelle, Ereignisalter, Quelle, Fahrtrichtung, Prognoseunsicherheit und ETA zum Rastplatz getrennt erfassen.
- Nur Haltepunkte empfehlen, deren sichere Ausfahrt **vor** dem Stau liegt; Ausfahrts-Vorwarnpuffer dynamisch aus Fahrgeschwindigkeit und Manövervorlauf ableiten.
- Verkehrszeitgewinn nur bei ausreichend belastbarer Prognose ausweisen; ein Rastplatzbesuch ist nicht automatisch eine Zeitersparnis.
- Auflösung/Verlagerung des Staus führt zu Neubewertung, aber nicht zu wiederholten Pop-ups.

### Evidenz- und Komfortbewertung
- Hartes Gate: legal befahrbar, richtige Seite, erreichbare Zufahrt und Rückkehr, vollständige Route-via-POI mit Zusatzzeit <=600 Sekunden; 601 Sekunden = Ausschluss.
- WC-Evidenz explizit: vorhanden/bestätigt, unbekannt, nicht vorhanden. Ein Rastplatz ohne verifizierte WC-Ausstattung ist **kein** bestätigter WC-Treffer.
- Ranking unter den zulässigen Kandidaten: WC-Evidenz, Verkehrslage, minimale Zusatzzeit, sichere Abfahrt, Aktualität der POI-Daten, persönliche Präferenz. Höchstens drei begründete Alternativen.
- Quellen und Datenalter im Diagnosestatus; keine Öffnungszeiten, Belegung oder Verfügbarkeit ohne belastbare Quelle.

### Menschliche Kontrolle und lernende Präferenzen
- Route bleibt unverändert, bis Nutzer ausdrücklich bestätigt. Ablehnen/Später/Keine Vorschläge für diese Fahrt sind jederzeit möglich.
- Lernsignale ausschließlich aus bewussten Nutzerentscheidungen, optional und lokal gespeichert; nie stillschweigend Sicherheitsgrenzen verändern.
- Fahrzeit nach bestätigter Pause oder ausreichend sicher erkannter längerer Pause zurücksetzen, nicht nach kurzen Stopps.
- Fahrtstatus und Cooldown über Prozessneustart sicher wiederherstellen; keine Speicherung unnötiger Rohpositionen.

### Fehlertoleranz
- Ohne Internet: lokale POIs und lokales Routing, keine behaupteten Live-Staus.
- Ohne POI-Index: keine erfundenen Haltepunkte, normale Navigation unverändert.
- Ohne belastbare ETA/Abfahrtsgeometrie: keine Kandidatenfreigabe; eventuell allgemeiner Hinweis „Pause erwägen“ ohne falsches Ziel.
- Bei LLM-Ausfall: deterministische Policy und statische Texte. Bei Worker-Abbruch: vorherige gültige Route unverändert.
- Bei mehrfacher Fehlberechnung: Circuit Breaker, Telemetrie ohne GPS-Rohdaten, Reparaturticket an R2 Engineering Triage.

### Messbare Abnahmekriterien
1. Null automatische Zwischenziele; alle Vorschläge brauchen explizite Bestätigung.
2. Null Vorschläge mit nachgewiesenem Umweg >600 Sekunden; fehlende ETA führt zu Ausschluss.
3. Null als bestätigt dargestellte WCs ohne positive Evidenz.
4. Testfälle: Stau hinter/vor Rastplatz, ETA-Änderung, verpasste Ausfahrt, Offline-Start, App-Neustart, GPS-Jitter, Zeitsprung, keine POIs, Reroute.
5. Reproduzierbarer Vergleich: Auslösezeit, Ausfahrts-Vorlauf, Vorschlagsanzahl pro Fahrt, bestätigte/abgelehnte Vorschläge, falsche WC-Angaben, Akku-/CPU-Budget.
6. Feature-Flag bis Android-UI, Routing- und POI-Integration sowie Gerätetest erfolgreich sind.

**Status:** R2.8 als Umsetzungsauftrag spezifiziert, keine fertige App-Funktion. R1-Stabilisierung bleibt priorisiert.
