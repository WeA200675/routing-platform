# Surprise themes and source archive

## Theme discovery

The training center now includes a **Humor & Überraschung** track. It tests original, friendly theme proposals, user approval, clarity, disableability, and the rule that theme wording never changes maneuver truth. Built-in profiles include the original **Galactic**, **Weltraumoper**, and **Sitcom** themes.

Users may name a favorite genre or fictional reference. The local model should derive an original, generic theme instead of copying protected dialogue, a recognizable voice, or a character's exact mannerisms. A theme is only activated after explicit selection or enabled weekly discovery.

## Source archive

Source discovery exercises cover:

- traffic jams, hazards, closures, and delays;
- lane and destination data near the active route;
- source freshness, road identity, geometry, travel direction, license, attribution, and update time;
- conflicts between local maps, OSM, and traffic feeds;
- unavailable or stale sources.

A source record should retain URL or provider identifier, retrieved time, coverage location, direction, data type, freshness, license/attribution state, validation result, and rejection reason. Unknown or conflicting lane data stays **nicht verfügbar** and never becomes a spoken lane instruction.

## Safe runtime boundary

Humor and source-learning can influence presentation and training priority. They cannot mutate route geometry, route progress, GPS fusion, rerouting, or safety-critical maneuver wording. Traffic information is spoken only with a provider, timestamp, route match, and confidence; otherwise the app says that live data is unavailable.

The on-device training archive stores session IDs, test results, generated cases, and source-validation outcomes. It does not train model weights or silently upload user routes.
