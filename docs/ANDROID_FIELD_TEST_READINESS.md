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

For physical development testing, the candidate workflow additionally packages
a clearly separate `g620-field-test.apk`. That APK uses the existing
debug-only loopback route endpoint at `127.0.0.1:8787`. It requires a ready
route service on the host and:

```powershell
.\adb.exe reverse tcp:8787 tcp:8787
```

Before driving, the host route service must return HTTP 200 from `/ready`.
A missing/not-ready route service is a hard no-go for the field test. The
release APK remains the distribution artifact and must not be confused with the
field-test APK.

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
7. The host route service is ready and ADB reverse is active before route
   acquisition.

Any code or workflow change after the freeze creates a new candidate and
invalidates the previous APK as final field-test evidence.

## AI route-personalization milestones

The local AI now asks for a route-family priority in Preview (fastest, shortest or profile-optimized), passes that choice into the real route request, and offers a rating after successful live route acquisition. A positive rating is stored only when **Routenlernen** is enabled; it stores the selected family only, scoped to the active profile. Turning the setting off removes that saved preference. See [`AI_ROUTE_PERSONALIZATION_MILESTONES.md`](AI_ROUTE_PERSONALIZATION_MILESTONES.md) for acceptance gates and remaining work.

## Saturday field run

For the planned 2026-10-10 calibration drive, follow [`SATURDAY_CALIBRATION_RUNBOOK.md`](SATURDAY_CALIBRATION_RUNBOOK.md). The app exposes accepted/rejected sample counts and best observed accuracy under **Kalibrierwerte** in Preview settings after navigation stops; this summary is not shown while driving and does not certify a pass. The runbook is preparation only; the physical test remains NOT RUN until evidence is recorded from the actual device and exact candidate APK.

## Route service compatibility gate

For the debug field-test APK, use `tools/navigation_route_service.py` backed by the built Valhalla exporter and the test area's Valhalla config. It must bind to `127.0.0.1:8787`, answer `/ready` with HTTP 200, and receive the verified `adb reverse tcp:8787 tcp:8787` mapping. The separate `backend/osrm` Compose adapter is not a substitute for this gate: it does not implement the `/ready` endpoint or the debug development-header contract expected by the field-test flow. Do not begin a drive if the route service or any readiness check fails.
