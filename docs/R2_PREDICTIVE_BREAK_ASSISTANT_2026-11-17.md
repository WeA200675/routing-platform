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
