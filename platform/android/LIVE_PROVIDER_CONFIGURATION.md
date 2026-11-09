# Live provider and discovery configuration

The Android app accepts optional HTTPS endpoints through manifest metadata:

- `org.routingplatform.live.TRAFFIC_ENDPOINT`
- `org.routingplatform.live.HAZARD_ENDPOINT`
- `org.routingplatform.live.WEATHER_ENDPOINT`
- `org.routingplatform.live.POI_ENDPOINT`
- `org.routingplatform.live.API_KEY`

Empty values intentionally disable the corresponding provider. No demo data is generated.

Each endpoint must return either a JSON array or:

```json
{"events":[{"id":"…","kind":"TRAFFIC_JAM","latitude":48.1,"longitude":11.5,"severity":3,"confidence":0.9,"captured_at_epoch_ms":0,"valid_until_epoch_ms":4102444800000,"source":"provider"}]}
```

Supported kinds are `HAZARD`, `ROAD_CLOSURE`, `ACCIDENT`, `TRAFFIC_JAM`, `WEATHER`, and `POI_UPDATE`.

The adapter requires HTTPS, validates coordinates and freshness, and fails closed to offline navigation when a source is unavailable.

## Discovery governance

Discovery is opt-in and fail-closed. Configure:

- `org.routingplatform.discovery.MANIFEST_KEY_ID`
- `org.routingplatform.discovery.TRUSTED_MANIFEST_KEYS`

The trusted-keys value is a comma/semicolon/space separated allowlist. The navigation session uses the governed provider factory only when a real signature verifier is supplied by the application and both discovery metadata values are present. Without those values, the app keeps the existing cached provider path and does not claim that discovery is active. Without a verifier, no provider can be promoted by discovery.

Accepted and rejected provider observations are written to the persistent training FIFO via `DiscoveryTrainingEventSink`, so discovery quality can be trained and audited across process restarts.
