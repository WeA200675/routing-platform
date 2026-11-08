# Trip Intelligence – Gesamtumsetzung

Dieses Dokument bündelt die verbindliche Zielarchitektur für die Routing-App.

## Prioritäten

1. Sicherheit und Weiterfahrt
2. Gefahren, Sperrungen und Staus
3. stabile Positions- und Routenführung
4. Zeit, Verbrauch und Kosten
5. Komfort und persönliche Präferenzen
6. Training und Hintergrundoptimierung

## Echtzeitpfad

- Watchdog erkennt ausbleibenden Fortschritt, GPS-Ausreißer und Kartenmatch-Konflikte.
- Lokale Neuberechnung läuft ohne Cloud-Abhängigkeit.
- Haupt-, Sicherheits- und Fallbackroute werden parallel vorbereitet.
- Keine Netzwerkoperation darf Karten- oder Sprachführung blockieren.
- Live-Meldungen erhalten Quelle, Zeitstempel, Richtung, Gültigkeitsdauer und Vertrauenswert.
- Veraltete oder widersprüchliche Ereignisse werden verworfen.

## Fahrtoptimierung

Die Bewertung berücksichtigt gemeinsam:

- Verkehr, Gefahren, Sperrungen und Wetter
- Streckenqualität und Fahrzeugbeschränkungen
- Verbrauch, Tank-/Ladefüllstand und Reichweite
- Tank- und Ladepreise inklusive Umweg, Zeit und Mehrverbrauch
- Maut, Parken und Pausen
- persönliche Präferenzen und Fahrstil

## POI-System

Die Suche muss online und offline funktionieren:

- Name, Kategorie, Adresse und Sprache
- in der Nähe, am Ziel und entlang der Route
- Ziel oder Zwischenziel
- Tankstelle, Ladestation, Parkplatz, Rastplatz, Restaurant, Werkstatt, Hotel, Apotheke, Supermarkt und Toilette
- Ranking nach Entfernung, Umweg, Öffnungsstatus, Aktualität, Qualität und Präferenz

## Training

```
lokales Ereignis
 -> FIFO-Synchronisierung
 -> Qualitätsprüfung
 -> Replay und Regression
 -> Shadow/Canary
 -> signiertes Artefakt
 -> Aktivierung
 -> Rollback bei Verschlechterung
```

Ungeprüfte Trainingsdaten dürfen nie direkt Sicherheits- oder Routingentscheidungen verändern.

## Android-Benutzerfluss

- Discovery beim Start und bei Wiederverbindung
- automatische Live-Synchronisierung
- Offline-FIFO bei Ausfall
- automatische Nachsynchronisierung
- Statusanzeige ohne manuelle Wartung
- Positionsanzeiger: Pfeil, Fahrzeug oder Punkt

## Abnahme

- Navigation bleibt ohne Cloud funktionsfähig.
- Sicherheitsdaten werden vor Komfortdaten verarbeitet.
- Neuberechnung blockiert keine Anzeige.
- POI- und Tankempfehlungen haben nachvollziehbare Aktualität.
- Modellversionen sind signiert und rückrollbar.
- Jede automatische Entscheidung ist im Trainingscenter nachvollziehbar.
