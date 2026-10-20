# Online lane and destination metadata

The Android debug route path can supplement missing Valhalla maneuver metadata with OpenStreetMap data fetched through Overpass. This is a best-effort metadata lookup; it does not calculate, select, or modify a route.

## Lookup and validation

- The app requests data only for up to 24 upcoming exit, merge, left-turn, or right-turn maneuvers whose lane or destination fields are missing.
- The request contains only the maneuver approach coordinates and the road identity needed to find matching OSM ways. It does not send the live GPS fix, full route geometry, user identity, or AI prompt.
- Requests are batched into one Overpass query. The app tries `overpass-api.de` and, if needed, `overpass.kumi.systems`, with short connection/read timeouts and a response-size bound.
- A candidate way is accepted only when its OSM name/reference matches the approach road, its geometry lies close to the maneuver point, its heading agrees with the route approach, and there is no similarly plausible competing way.
- Direction-specific `turn:lanes:forward/backward` tags take precedence. Generic `turn:lanes` tags are accepted only for a one-way way. Exit or merge lanes are not marked as recommended unless the maneuver side is known.
- OSM `destination` values are labeled as **OSM-Kartenziel**, not presented as a photographed or verified road sign.
- The UI shows lookup status and OpenStreetMap attribution. A missing, ambiguous, stale-by-unknown, unavailable, or failed result remains unavailable. No model-generated lane is used.

## Fallback behavior

Network errors, invalid responses, missing road names/headings, ambiguous ways, or absent OSM tags do not fail route calculation. The existing Valhalla route remains active; the UI tells the driver to follow roadside signage when lane data remains missing. The online lookup can add metadata only and cannot alter route geometry or maneuver direction.

The public Overpass endpoints are best effort and can rate-limit or become unavailable. A production deployment with sustained traffic should use an appropriately operated, licensed data service or a self-hosted Overpass instance.

## Training

The on-device training center contains source-review exercises for choosing sources, checking road/direction/geometry, comparing timestamps and attribution, handling conflicts, and reporting unavailable data. These are inference/evaluation cases; they do not change model weights and do not authorize the model to invent road metadata.
