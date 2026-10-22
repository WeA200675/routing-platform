# Guidance Integrity, Positioning, and Local Training Archive

## Spoken maneuver integrity

Every spoken maneuver is derived from the active route contract. For a directional maneuver, the Android presentation layer cross-checks:
- the Valhalla maneuver type,
- any normalized provider-side value,
- route bearings before and after the maneuver.

A material disagreement suppresses both the turn direction and road name and asks the driver to follow the posted signs. When a street name appears in the provider instruction but does not match the route maneuver's street-name or destination-sign metadata, the voice keeps only a validated direction. Unknown or incomplete fields remain unknown.

The Valhalla adapter reads both the adapter's existing `bearing_before`/`bearing_after` fields and Valhalla's native `begin_heading`/`end_heading` fields. Geometry remains unchanged by this check. The check is a fail-safe for contradictory metadata; it cannot prove that the source map itself reflects current road signage.

## Android positioning pipeline

`AndroidLocationSource` is the one source of direct Android location fixes for route progress:
- API 31 and later: use the system `fused` provider when it is enabled.
- If the fused provider is unavailable, or on older Android versions: use enabled GPS and network providers. Their callbacks enter the same runtime queue and fusion engine.
- `AndroidNavigationRuntimeController` passes every location sample through one ordered queue, the position-fusion engine, route-matching, and the progress safety gate.
- Raw GNSS measurements and satellite status are evidence only. Accelerometer, gyroscope, and rotation-vector samples support motion agreement and bearing stability. Wi-Fi RTT may replace a fix only after anchor verification and an accuracy-improvement check.
- Planning-location capture is separate from live navigation progress; it supplies a route origin or map selection.

No second provider or sensor path independently advances the active route. On older Android versions, GPS and network fixes are both inputs to the shared fusion path, so a weak network fix can still affect the evidence stream. The Pixel 10 Pro path should select the fused provider when Android reports it enabled.

## Training session preservation and series

The training center can run an interleaved series across selected topics. Results carry a session ID so a worker from an older order cannot alter or enqueue work for a newer session. Starting a new series cancels the previous work and archives its final session state. The local archive keeps up to 100 sessions and 10,000 rule-check results.

Training sessions run local model inference and save rule-check metrics. They do not update model weights or create a LoRA adapter. The Social AI memory store is a separate store for reviewed user preferences and knowledge; raw training outputs are not automatically promoted into that memory.

## Field validation

CI verifies source and build contracts. It cannot validate a specific interchange against live signs or prove GNSS behavior on a moving vehicle. Check route direction and signage during a safe, stationary review of a planned route before field use, and report the route ID plus the spoken phrase if a mismatch remains.
