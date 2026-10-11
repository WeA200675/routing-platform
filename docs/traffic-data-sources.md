# Live traffic and departure insights

## Providers found in the deep search

The app consumes a normalized, timestamped traffic report. The route engine alone does not provide live congestion evidence; a new route response must never be labelled as a traffic report.

| Provider/source | Coverage and useful fields | Access/licensing notes |
| --- | --- | --- |
| [OpenConditions](https://github.com/OpenConditions/openconditions) | Self-hostable open-data collector. Includes Die Autobahn (Germany) event feeds; emits GeoJSON, DATEX II, Valhalla exclusions, and SSE. Sensor-based flow-speed coverage is only partial. | Ingest service is AGPL-3.0; reusable packages are Apache-2.0. Each upstream feed retains its own terms. Requires a running service and configured feeds. |
| [opentransportdata.swiss](https://opentransportdata.swiss/en/cookbook/road-traffic-cookbook/traffic-situations/) | Swiss real-time DATEX II incidents, congestion, updates, disruption duration; some messages include estimated disruption length/duration. | An API token is required. Feed disruption duration is not necessarily queue delay; use estimatedDelaySeconds only when source explicitly provides that metric. |
| [ASFINAG Content Portal](https://contentportal.asfinag.at/) | Austrian motorway events, speed/flow, queue length, current and predicted travel times, expected delays. | Registration and attribution under CC BY 4.0 plus portal conditions. Do not ship credentials in the APK. |
| [Open Data Hub DATEX II](https://docs.opendatahub.com/use-data/datexii-api/reference/) | Regional South Tyrol traffic events in DATEX II. | Regional feed only; check each feed's license/access metadata. |

These sources are not a single free, complete, route-wide DACH traffic-time API. For now, the Android app has no traffic endpoint configured. Consequently, it displays no fabricated live delay. Bluetooth and Wi-Fi discovery are not traffic feeds; live information is retrieved over HTTPS from an explicitly configured service. A paired receiver may be added as a separate user-consented source later.

## Product contracts implemented

- TrafficCongestionReport keeps provider, attribution, observed/valid timestamps, confidence, disruption duration, and queue delay distinct.
- Reports older than five minutes, not-yet-valid reports, and expired reports cannot drive an ETA or reroute offer.
- A congestion ETA is exposed only when the source explicitly supplies an estimated delay. Event duration is never presented as time spent waiting.
- An alternative route must save at least two minutes after adding the reported delay. The 15-second offer keeps the existing route unless accepted. Expiry does not mutate navigation.
- Local trip statistics are coordinate-free. When route learning is enabled, the time-of-week model uses the arithmetic mean of at least three observations for the same route, weekday, and a departure hour within ±1 hour. Departure planning prefers fresh live delay, then the historical mean, then the route baseline; the UI can label which evidence it used.

## Credential setup

Keep tokens on the route/traffic service host. Do not place provider secrets in Android manifest metadata, source control, or the APK. Before enabling any provider, configure its source attribution, coverage, poll limits, retention, and upstream-license requirements.