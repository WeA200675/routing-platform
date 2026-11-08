# Große Meilensteinplanung – Trip Intelligence

## Zielbild

Die Routing-App wird zu einer robusten, vorausschauenden Fahrtassistenz mit Offline-Fallback, priorisierten Live-Daten, POI-/Tankoptimierung, automatischer Fehlerbehandlung und kontrolliertem Lernen.

## M0 – Baseline und Build-Sicherheit

**Ziel:** Reproduzierbarer Ausgangspunkt.

- Repository-Stand einfrieren
- Android- und Core-Build reproduzierbar machen
- bestehende Tests erfassen
- Testdaten und Offline-Fixtures definieren
- CI-Prüfung für Kotlin, Core und Backend ergänzen

**Abnahme:**

- Build läuft reproduzierbar
- bestehende Tests sind grün oder dokumentiert
- keine neuen Warnungen werden verschwiegen

## M1 – Echtzeit-Sicherheitskern

**Ziel:** Navigation darf bei Problemen nicht hängen bleiben.

- Watchdog in den laufenden Navigationszyklus einhängen
- Positionsalter und Fortschrittsalter messen
- Recovery-Koordinator ausführen
- doppelte Recovery-Aufträge verhindern
- harte Zeitbudgets pro Entscheidung
- lokaler Fallback ohne Cloud

**Abnahme:**

- künstlicher GPS-/Progress-Ausfall löst Recovery aus
- aktuelle Route bleibt sichtbar, bis Ersatzroute geprüft ist
- keine Blockierung der Sprach- oder Kartenanzeige

## M2 – Parallele Routen- und Recovery-Engine

**Ziel:** Ersatzrouten schnell und sicher vorbereiten.

- Haupt-, Sicherheits- und Fallbackroute parallel berechnen
- Routenkandidaten sicherheitsbasiert bewerten
- atomare Übernahme einer geprüften Route
- Rollback auf letzte stabile Route
- erneute Berechnung nach verpasstem Manöver
- sichtbarer Status für Berechnung und Übernahme

**Abnahme:**

- Route kann ohne sichtbaren Sprung gewechselt werden
- unsichere Kandidaten werden abgelehnt
- jede Übernahme ist nachvollziehbar protokolliert

## M3 – Live-Daten-Fusionsschicht

**Ziel:** Staus, Gefahren und Sperrungen priorisiert verwerten.

- Provider-Adapter definieren
- mehrere Datenquellen unterstützen
- Quelle, Alter, Richtung, Entfernung und Vertrauen bewerten
- Ereignisse deduplizieren
- widersprüchliche Meldungen behandeln
- Ablaufzeiten und lokalen Cache einführen
- Sicherheitsereignisse vor Komfortdaten priorisieren

**Abnahme:**

- Gefahren und Sperrungen verdrängen Komfortdaten
- abgelaufene Daten werden nicht verwendet
- Provider-Ausfall führt zu Cache-/Offline-Fallback

## M4 – POI-System

**Ziel:** zuverlässige Suche online und offline.

- Suche nach Name, Kategorie, Sprache und Adresse
- Suche in der Nähe, am Ziel und entlang der Route
- POI als Ziel oder Zwischenziel
- Ranking nach Entfernung, Umweg, Öffnung, Aktualität, Qualität und Preis
- lokale Favoriten und zuletzt verwendete Ziele
- Online-/Offline-Provider-Fallback

**Abnahme:**

- POI-Suche funktioniert ohne Internet mit Cache-Daten
- mehrere Treffer werden nachvollziehbar sortiert
- Zwischenziel kann sicher in die Route übernommen werden

## M5 – Tank-, Lade- und Kostenoptimierung

**Ziel:** optimale Kosten und Reichweite auf kurzen und langen Fahrten.

- Verbrauchsmodell pro Fahrzeug
- Tankfüllstand und Reichweite
- Tankstellenpreise und Ladepreise
- Preisvorteil gegen Umweg, Zeit und Mehrverbrauch rechnen
- Tank-/Ladestopps vor Fahrtbeginn planen
- Reichweitenreserve berücksichtigen
- Park-, Maut- und Fahrtkosten integrieren

**Abnahme:**

- keine Empfehlung außerhalb der sicheren Reichweite
- Kostenrechnung erklärt Preis, Umweg und Zeit
- fehlende Preise führen zu sicherem Fallback statt falscher Genauigkeit

## M6 – Wetter, Pausen und Komfort

**Ziel:** gesamte Fahrtqualität optimieren.

- Wetter- und Streckenrisiken
- Baustellen, Maut, Umweltzonen und Fahrzeugbeschränkungen
- Pausen nach Fahrtdauer, Müdigkeit, Tank-/Ladestopp und POIs
- Parkplatzsuche und Gebühren
- ruhige, sichere oder verbrauchsarme Routenvorlieben
- persönliche Präferenzen

**Abnahme:**

- Empfehlungen erklären ihren Grund
- Komfort darf Sicherheitsentscheidungen niemals überstimmen
- bei fehlenden Daten bleibt die Navigation funktionsfähig

## M7 – Android-Benutzeroberfläche

**Ziel:** alle Funktionen ohne technische Bedienung nutzbar machen.

- Cloud-/Offline-Status
- Live-Datenstatus und Datenalter
- Neuberechnungsstatus
- POI- und Tankempfehlungen
- Positionsanzeiger-Auswahl
- Trainingsstatus
- manuelle Aktualisierung nur optional
- verständliche Fehlermeldungen

**Abnahme:**

- Benutzer muss keine URLs oder Dateien pflegen
- Status ist während der Fahrt verständlich sichtbar
- sicherheitskritische Hinweise werden nicht von Komfortanzeigen verdeckt

## M8 – Training und kontrollierte Updates

**Ziel:** echte Verbesserungen ohne Rückschritte.

- Ereignisse automatisch sammeln
- FIFO-Grenzen und Puffer
- Normalisierung und Qualitätsprüfung
- Replay gegen bekannte Fehler
- Performance- und Regressionstests
- Shadow- und Canary-Phase
- signierte Artefakte
- automatische Aktivierung
- Rollback bei Verschlechterung

**Abnahme:**

- ungeprüfte Daten verändern keine Sicherheitslogik
- jede Modellversion ist rückrollbar
- Performanceverlauf ist im Trainingscenter sichtbar

## M9 – Cloud- und Betriebsrobustheit

**Ziel:** wartungsarmer Dauerbetrieb.

- systemd-/Container-Überwachung
- automatische Neustarts
- Discovery- und Tunnelüberwachung
- Backup und Wiederherstellung
- Speicher- und FIFO-Monitoring
- Health-Endpunkte
- Alarmierung bei Ausfall
- Kosten- und Ressourcenlimits

**Abnahme:**

- API, Tunnel und Dashboard starten nach Neustart automatisch
- Speichermaximum kann nicht überschritten werden
- Wiederherstellung ist getestet

## M10 – Gesamtintegration und Release

**Ziel:** gemeinsamer testbarer Gesamtstand.

- Android-Unit- und Integrationstests
- Offline-/Online-Wechsel
- GPS-Ausfall
- Stau-/Gefahrenereignisse
- POI- und Tankplanung
- Neuberechnung
- Rollback
- längere Testfahrten
- APK-Build und Installationspaket
- Release-Dokumentation

**Abnahme:**

- Gesamtbuild reproduzierbar
- kritische Tests grün
- keine ungeprüften Kernpfade
- ein zusammenhängender Release-Stand für die App

## Abhängigkeiten

```text
M0
 ↓
M1 → M2
 ↓    ↓
M3 → M4 → M5 → M6
          ↓
         M7
          ↓
         M8 → M9 → M10
```

## Grundregel

Sicherheit, Weiterfahrt und Offline-Fähigkeit haben immer Vorrang vor Training, Komfort und Datenaktualität.
