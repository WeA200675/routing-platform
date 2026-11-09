# Trainierbare Quellen-Discovery

Die Discovery-Schicht kann Quellen für folgende Bereiche bewerten:

- Live-Verkehr und Gefahren
- Routing-Engines
- POI
- Tank-/Ladepreise
- Wetter
- Trainingsartefakte

Discovery liefert zunächst nur Vorschläge. Eine Quelle wird erst nach diesen Prüfungen als reviewfähig markiert:

1. HTTPS
2. vertrauenswürdiger Host
3. ausreichende Konfidenz
4. vorhandene Evidenz
5. spätere explizite Aktivierung

Nicht vertrauenswürdige oder unsichere Quellen werden verworfen und dürfen keine Navigation, Trainingsdaten oder Artefakte beeinflussen. Dadurch kann die Trainings-KI neue Quellen lernen, ohne automatisch fremde Endpunkte zu aktivieren.
