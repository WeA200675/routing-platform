# Android field-test readiness (M1-M10)

This document defines the pre-drive gate for the next Android field test. It does
not replace P35 physical-device evidence or P36 GA promotion evidence.

## M1 audit findings

The product already contains a fail-closed position pipeline with Android
location, IMU/sensor evidence, raw GNSS observations, environment evidence,
dead reckoning, integrity monitoring, confidence hysteresis, multi-hypothesis
route matching and route-progress safety gating. These components must be
improved and measured rather than bypassed.

The field failure observed on the previous candidate had two independent
causes:

* the release build had no live route endpoint, so the bundled Vaduz bootstrap
  route remained active;
* the local assistant only accepted a strict symbolic model contract, so an
  ordinary street address could end in a model-format clarification instead of
  continuing through deterministic destination search.

The driving UI also exposed diagnostic/secondary controls that consumed map
space while automatic positioning was active.

## M2-M3 destination and local-assistant gate

Ordinary address text is now allowed to fail over from the local model to a
bounded deterministic search query. The fallback never creates coordinates.
Coordinates may only enter routing from the trusted destination-search source,
favorites, or an explicit map selection.

The strict local-model contract remains authoritative for symbolic intents.
Malformed model output is not parsed permissively.

## M4 routing gate

Production/release routing remains HTTPS-only. No cleartext or loopback
exception is added to the release manifest.

The field-test APK has a debug-only on-device Valhalla engine. Import the DACH
tile archive from **Offline-Routing** settings; the app hashes the complete copy
and requires a successful local Vaduz route before activating it. With a
validated archive, supported route families are calculated on the phone without
USB or a running PC route service.

The debug build keeps the loopback route service at `127.0.0.1:8787` as an
optional fallback for unsupported route families or local routing failures.
Only that fallback requires a ready host service and:

```powershell
.\adb.exe reverse tcp:8787 tcp:8787
```

The release APK remains the distribution artifact and must not be confused with
the field-test APK.

## M5-M6 positioning and safety gate

Android location samples now retain native speed, speed accuracy, bearing and
bearing accuracy when the platform supplies valid values. The integrity layer
only promotes speed/bearing evidence within explicit accuracy bounds; otherwise
it falls back to existing derived motion evidence or leaves bearing unknown.

Raw GNSS, IMU, environment evidence, dead reckoning and route matching remain
capability-based. No unavailable sensor is fabricated and no uncertain position
is snapped to the route merely to advance progress. Safety Hold remains the
correct result when the trusted-position contract is not met.

## M7 drive UI gate

During automatic navigation, diagnostic secondary controls are removed from the
primary driving surface. The destructive stop control is compact and the
move-side control is not shown while navigating. Preview/settings behavior is
unchanged.

## M8-M9 robustness gate

The existing lifecycle, reroute, persistence, corruption, resource-bound,
concurrency and safety tests remain mandatory. New tests cover deterministic
assistant fallback and quality-gated native motion evidence.

Known recent failure classes remain preflight checks:

* host JVM tests must not depend on an Android runtime accidentally;
* broad workflow path globs must satisfy narrower coverage requirements;
* candidate evidence is valid only for its exact source SHA;
* concurrency ownership handoff must be reviewed for missed wakeups;
* structural acceptance audits are not content or physical-device acceptance;
* test-signed, production-signed and store-published states must stay distinct.

## M10 candidate freeze

A field-test candidate is ready only when all of the following are true on one
unchanged source SHA:

1. Core CI succeeds.
2. Android CI succeeds.
3. iOS Contract CI succeeds.
4. P1-P36 Production Candidate succeeds.
5. The candidate artifact contains both `g620-release.apk` and
   `g620-field-test.apk` with SHA-256 sidecars.
6. The exact field-test APK SHA-256 is recorded before installation.
7. For routes that need the optional PC fallback, the host service is ready
   and ADB reverse is active. The DACH archive has passed the on-device import
   and route check before offline field use.

Any code or workflow change after the freeze creates a new candidate and
invalidates the previous APK as final field-test evidence.

## AI route-personalization milestones

The local AI now asks for a route-family priority in Preview (three common choices plus nine expandable variants), passes that choice into the real route request, and offers a rating after successful live route acquisition. A positive rating is stored only when **Routenlernen** is enabled; it stores the selected family only, scoped to the active profile. Turning the setting off removes that saved preference. See [`AI_ROUTE_PERSONALIZATION_MILESTONES.md`](AI_ROUTE_PERSONALIZATION_MILESTONES.md) for acceptance gates and remaining work.

## Saturday field run

For the planned 2026-10-10 calibration drive, follow [`SATURDAY_CALIBRATION_RUNBOOK.md`](SATURDAY_CALIBRATION_RUNBOOK.md). The app exposes accepted/rejected sample counts and best observed accuracy under **Kalibrierwerte** in Preview settings after navigation stops; this summary is not shown while driving and does not certify a pass. The runbook is preparation only; the physical test remains NOT RUN until evidence is recorded from the actual device and exact candidate APK.

## Route service compatibility gate

The field-test build first tries the validated on-device DACH archive. Confirm
**DACH-Kartenpaket bereit** in Offline-Routing settings and let the import route
check complete. Then test a supported route with the phone disconnected from
USB and the PC service stopped. The route status must report a live route.

For the optional PC fallback, use `tools/navigation_route_service.py` backed by
the built Valhalla exporter and DACH config. It must bind to `127.0.0.1:8788`,
answer `/ready` with HTTP 200, and receive
`adb reverse tcp:8787 tcp:8788`. The `backend/osrm` Compose adapter is not a
substitute because it does not implement this `/ready` endpoint or the debug
development-header contract.

## Route-sign, map and roadside-stop acceptance addendum

The current branch now carries destination-sign text and lane indications
through the JSON route contract into the top maneuver panel. The OSRM adapter
only emits lane arrows when the response contains one unambiguous intersection
with lane data. A marked lane is identified as route-valid by the provider; it
is not inferred from route geometry. Missing or ambiguous values must leave the
lane diagram absent, with the spoken/displayed maneuver and road signs remaining
the fallback.

Before any supervised drive, exercise both an exit-right fixture and an
exit-left fixture. Compare the engine maneuver side against the instruction,
sign text and lane arrows. Any side disagreement, lane ordering mismatch, or
arrow shown without provider evidence is a no-go. A field drive must include a
passenger recording observations; never provoke an unsafe maneuver to test the
app.

The Terra profile overlays topographic raster tiles over the existing vector
street style. If the relief service is unavailable, the street layer below it
must remain visible. The app identifies the map data source on screen. Verify
both connected use and airplane-mode fallback while parked before testing a
route.

Rastplatz search is explicit (`Raststätte mit WC`, `Raststätte ohne WC`, or
`Raststätte`). It queries OpenStreetMap near a fresh planning position and
labels the WC state as present, absent, or unknown from the source tags. The
user-facing message discloses that the location is sent to the search provider.
Unknown tags must not satisfy either WC filter. Route selection uses the
existing route acquisition and route comparison gates; OSM area centroids do
not certify an entrance, so the result asks the driver to check the signed
access locally. If the provider is unavailable, normal address search remains
available and does not claim WC information.

The speed-enforcement alert policy remains fail-closed: no alert may be
presented without an explicit current rule for the active country. The policy
utility is not a country-law catalog or a data feed; no camera-alert release is
accepted until a reviewed country catalog, lawful data source, UI setting,
cross-border behavior and corresponding tests are supplied. Germany remains
prohibited during vehicle operation under the current StVO rule.
