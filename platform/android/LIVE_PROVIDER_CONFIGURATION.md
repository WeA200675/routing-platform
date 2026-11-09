# Live provider configuration

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
