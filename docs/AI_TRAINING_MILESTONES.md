# KI-Implementierung und Trainingsentwicklung

Die KI bleibt beratend. Routing, Sicherheitsentscheidungen, Koordinaten, Geometrie,
Manöver, ETA und Verkehrsstatus bleiben deterministisch. KI-Vorschläge werden vor
Übernahme durch den Benutzer geprüft.

## M1 – Beobachtungs- und Beweiskette

Alle Eingaben, erwarteten und beobachteten Ergebnisse, Laufzeiten, Fehlerursachen,
Quellversion und Daten-Split werden mit Fall-ID erfasst. Persönliche Daten,
Rohkoordinaten und Sicherheitsbeweise bleiben lokal und werden vor Prompts minimiert.

## M2 – Fallkatalog und Priorisierung

Fälle werden nach Navigation, Positionierung, Sprache, Offline-Betrieb, Verkehr,
Ressourcen, UI und Sicherheit kategorisiert. Kritische Fälle blockieren Promotion.
Die KI darf nur aus vorhandener Evidenz Fälle ableiten und muss Unbekanntes markieren.

## M3 – Benutzerprüfung

Vorschläge erscheinen einzeln, stabil sortiert und mit Fortschrittsanzeige. Sichtbar
sind Eingabe, Erwartung, Beobachtung, Ursache, Signale, Datenqualität, Konfidenz,
betroffene Codebereiche und vorgeschlagene Tests. Aktionen: übernehmen, ablehnen,
zurückstellen oder weitere Informationen anfordern.

## M4 – Regression und Simulation

Übernommene Fälle werden in reproduzierbare Unit-, Integrations-, UI- oder
Gerätesimulationstests übersetzt und gegen Training, Validation und unbekannte Tests
ausgeführt. Promotion erfordert Verbesserung ohne Regression und innerhalb der
Ressourcenlimits.

## M5 – Lernen bestehender Fähigkeiten

Zuerst lernt die KI nur bereits implementierte Fähigkeiten: GPS-Plausibilität,
Richtungsprüfung, Ansagezeitpunkt, Routenfamilienpräferenz, Fehler-Recovery und
Antwortlänge. Sie verändert Policies und Prioritäten, nicht die autoritative
Routinglogik. Jede Fähigkeit erhält Schwellenwert, Split, Driftstatus und Rollback.

## M6 – Varianten, Promotion und Rollback

Varianten werden einzeln bewertet. Promotion-Stufen: Observe → Suggest → Simulator →
Device → Production Candidate. Drift, fehlende Evidenz, kritische Fehler oder
Ressourcenüberschreitung führen fail-closed zu Suggest oder Rollback.

## M7 – Aktives Lernen und Recovery

Verbesserbare Fehler werden nach Ursache gruppiert und begrenzt erneut vorgeschlagen.
Kapazitätsgrenzen führen zu Deferred statt Datenverlust. Deferred-, Recovered- und
offene Fälle bleiben getrennt sichtbar und behalten ihre Historie.

## M8 – Geräte-, Datenschutz- und Produktionsnachweis

Modell, Runtime, Trainingsdaten, Build und Artefakte werden über Hashes gebunden.
Physische Geräteprüfung ist eigener Nachweis. Produktion erhält nur Varianten mit
grüner automatischer Evidenz und dokumentierter Benutzerprüfung.

## Falllebenszyklus

`observed → proposed → user_review → accepted/rejected/deferred → simulated → device_verified → promoted`

Ablehnung, Drift oder Regression erzeugen einen erklärten Zustand und keinen stillen
Datenverlust.

## Nächste Arbeitsmeilensteine für den nächsten stabilen Stand

### N1 – Review-Queue produktiv verdrahten

Die Queue wird an den Trainingsreport und die Dashboard-Aktionen angeschlossen.
Entscheidungen werden lokal versioniert gespeichert, nach Neustart wiederhergestellt
und niemals automatisch als Benutzerfreigabe interpretiert.

### N2 – Testfall-Generator mit Sicherheitsgrenzen

Aus Fehlern dürfen nur begrenzte, anonymisierte und reproduzierbare Fälle entstehen.
Jeder generierte Fall braucht erwartetes Ergebnis, vorgeschlagenen Test und Ursache.
Unvollständige Fälle bleiben `NeedsInformation` und blockieren Promotion.

### N3 – Trainingslauf als überprüfbare Transaktion

Ein Trainingslauf erhält Eingabe-Hash, Modell-/Runtime-Hash, Fallliste, Vorher-/Nachher-
Scores und Ressourcen-Snapshot. Bei Abbruch wird keine Variante promoted; die letzte
bekannte gute Variante bleibt aktiv.

### N4 – Drift- und Regressionswächter

Die Kategorien GPS, Richtung, Timing, Route, Offline, Ressourcen und Sprache erhalten
eigene Schwellenwerte. Ein global guter Score darf eine kritische Kategorie nicht
überdecken. Kritische Regressionen erzwingen Rollback.

### N5 – Release-Gate für morgen

Vor einem Build müssen statische Vertragsprüfungen, Unit-/JVM-Tests, Review-Queue-
Tests, Daten-Split-Prüfungen und ein reproduzierbarer Trainingsreport vorhanden sein.
Build, Lint und Gerätetest bleiben ein separater Auftrag und werden erst danach auf
dem unveränderten Commit ausgeführt.

### N6 – Harte Promotion-Gates

Promotion ist nur möglich, wenn alle Vorschläge geprüft, kritische Fälle akzeptiert,
unbekannte Tests bestanden, Drift ausgeschlossen, Ressourcenlimits eingehalten und
der Nachher-Score nicht schlechter als der Vorher-Score ist. Andernfalls bleibt der
Stand auf Hold; bei expliziter Regression ist Rollback erforderlich.
