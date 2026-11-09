# Masterplan: Trip Intelligence Gesamtstand

## Ziel

Die Routing-App soll eine Fahrt vorausschauend, sicher, kostenbewusst und ohne manuelle Cloud-Schritte begleiten. Echtzeitdaten dÃ¼rfen die Navigation verbessern, aber niemals ungeprÃ¼ft die Sicherheitslogik umgehen.

## PrioritÃ¤ten

1. Sicherheit und Weiterfahrt
2. gÃ¼ltige Gefahren-, Sperr- und Staudaten
3. stabile Positions- und RoutenfÃ¼hrung
4. Zeit, Verbrauch und Kosten
5. Komfort und persÃ¶nliche PrÃ¤ferenzen
6. Training und Hintergrundoptimierung

## Laufzeitarchitektur

```text
Sensoren + GNSS + Karte + Live-Quellen
                  |
                  v
       Ereignis- und Vertrauensfusion
                  |
        +---------+----------+
        |                    |
        v                    v
  Safety Watchdog       Live Context Store
        |                    |
        +---------+----------+
                  v
       parallele Routenkandidaten
                  |
          Sicherheitsgates
                  |
          atomare Ãbernahme
                  |
       Training und Replay spÃ¤ter
```

## Echtzeitverhalten

- HÃ¤ngerkennung Ã¼ber Positionsalter, Fortschritt, Kartenmatch und Sensorkonsistenz.
- Lokale Sofort-Neuberechnung ohne Cloud-AbhÃ¤ngigkeit.
- Parallel vorbereitete Haupt-, Sicherheits- und Fallbackroute.
- Feste Zeitbudgets pro Entscheidung; kein blockierender Netzwerkaufruf.
- Live-Meldungen mit Quelle, Zeitstempel, Fahrtrichtung, GÃ¼ltigkeit und Vertrauenswert.
- Sicherheitskritische Meldungen verdrÃ¤ngen Komfort- und Trainingsaufgaben.
- Veraltete, widersprÃ¼chliche oder nicht plausibel zur Route passende Meldungen werden verworfen.

## Fahrtoptimierung

Der Fahrtnutzen wird aus Zeit, Sicherheit, Verbrauch, Tank-/Ladekosten, Maut, Parken, Pausen, Wetter, Verkehr, POI-QualitÃ¤t und persÃ¶nlichen PrÃ¤ferenzen berechnet. Ein Preisvorteil darf nur berÃ¼cksichtigt werden, wenn Umweg, Zeit und Mehrverbrauch gegengerechnet wurden.

## POI-System

- Suche nach Name, Kategorie, Sprache und Adresse.
- Suche in der NÃ¤he, am Ziel und entlang der Route.
- Nutzung als Ziel oder Zwischenziel.
- Offline-Cache plus Online-Provider mit Fallback.
- Ranking nach Entfernung, Umweg, Ãffnungsstatus, AktualitÃ¤t, QualitÃ¤t und NutzerprÃ¤ferenz.
- Kategorien: Tankstelle, Ladestation, Parkplatz, Rastplatz, Restaurant, Werkstatt, Hotel, Apotheke, Supermarkt und Toilette.

## Training und Updates

```text
Ereignis lokal sammeln
 -> FIFO-Synchronisierung
 -> QualitÃ¤tsprÃ¼fung
 -> Replay und Regression
 -> Shadow/Canary
 -> signiertes Artefakt
 -> automatische Aktivierung
 -> Rollback bei Verschlechterung
```

UngeprÃ¼fte Trainingsdaten dÃ¼rfen nie direkt die Sicherheits- oder Routingentscheidung verÃ¤ndern.

## Benutzererlebnis

- Keine manuelle Synchronisierung.
- Cloud-Discovery beim Start und bei Wiederverbindung.
- Automatische Live-Aktualisierung.
- VerstÃ¤ndliche Hinweise bei HÃ¤nger, Neuberechnung, Offlinebetrieb und Datenkonflikten.
- Positionsanzeiger: Pfeil, Fahrzeug oder Punkt; Auswahl wird gespeichert.

## Abnahmekriterien

- Navigation bleibt bei Cloud-Ausfall funktionsfÃ¤hig.
- Sicherheitsdaten werden vor Komfortdaten verarbeitet.
- Eine Neuberechnung blockiert weder Kartenanzeige noch SprachfÃ¼hrung.
- Jede aktive Modellversion ist rÃ¼ckrollbar.
- POI-Suche liefert bei Provider-Ausfall einen lokalen Fallback.
- Tank-/Ladeempfehlungen enthalten Preis, Reichweite, Umweg und AktualitÃ¤t.
- Jede automatische Entscheidung ist im Trainingscenter nachvollziehbar.
