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
