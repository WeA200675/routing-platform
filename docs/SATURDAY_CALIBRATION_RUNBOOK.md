# Saturday calibration drive runbook

Target date: Saturday, 2026-10-10. This runbook prepares the physical Android field test; it does not claim that a drive has been performed or that calibration has passed.

## Scope and safety boundary

The current `NavigationDeviceCalibration` learner records bounded quality evidence only: accepted/rejected direct observations and the best reported horizontal accuracy. It does not tune route costs, invent coordinates, change route geometry, or advance navigation. Keep that boundary intact during the drive.

Use a lawful, familiar route with safe places to stop. A passenger operates the test device and records observations; the driver does not interact with the app while moving. Stop the run if the app, device, weather, traffic, or route conditions make the test distracting or unsafe. Do not put real coordinates, raw location traces, personal identifiers, or unreviewed screenshots in GitHub.

## Freeze and artifact gate

1. Select one immutable candidate SHA and record it before starting. The source SHA, workflow evidence, field APK and SHA-256 sidecar must match.
2. Require successful Core CI, Android CI, iOS Contract CI and P1–P36 Production Candidate runs for that same SHA.
3. Download `g620-field-test.apk` and its `.sha256` sidecar from that candidate run. The field APK is a debug-only test artifact. Do not substitute `g620-release.apk` or use the field APK for distribution.
4. Verify the APK checksum before installation and record the exact SHA-256 in the local test record. Recheck the installed APK identity with the existing device harness.
5. Freeze the candidate. Any source or build-workflow change requires a new candidate and invalidates the previous artifact as final evidence.

## Host and device preflight

1. Confirm the Android device is online over ADB and note device model, Android version, app version and candidate SHA.
2. Start `tools/navigation_route_service.py` backed by the locally built Valhalla route exporter and the read-only Valhalla config for the test area. Example (replace both placeholders with real absolute paths):

   ```sh
   python3 tools/navigation_route_service.py --listen 127.0.0.1 --port 8787 --executable /absolute/path/to/route-exporter --config /absolute/path/to/valhalla-test.json
   ```

   Keep it running on the host. The Android field APK sends the development header and expects this service contract. The `backend/osrm` Compose adapter is not interchangeable for this field APK: it is a separate adapter and does not expose the required `/ready` endpoint.
3. Confirm `http://127.0.0.1:8787/ready` returns HTTP 200 on the host and reports every dependency check as ready. If it is not ready, do not connect the device or drive.
4. Establish and verify the USB reverse mapping with `adb reverse tcp:8787 tcp:8787`. If the mapping is missing, do not drive.
5. Confirm location permission, precise-location availability, sensor availability and app foreground behavior. Do not infer unavailable capability.
6. Confirm the displayed origin and selected destination correspond to the planned test. The bundled Vaduz bootstrap route is not valid evidence of a newly acquired route.
7. In the parked Preview, exercise the local AI priority question. Confirm that the three common choices and at least one expanded route-family choice produce the corresponding route-family request; do not treat the bundled bootstrap route as a successful live preview.
8. If testing learning, enable **Routenlernen** in AI settings first. Rate a successful live preview positively, confirm the stored recommendation is profile-local, then disable learning and confirm that the recommendation is removed. Repeat once with learning disabled and verify that the rating is not retained.
9. Record the calibration profile's starting counters. Do not reset a personal profile; use an approved test profile or record the pre-run values.

## Drive sequence

Run only after all preflight checks pass. Use the same candidate and device throughout. Accept the first-use calibration disclosure while parked. After the drive, stop navigation and open **Kalibrierwerte** in Preview settings to read the accepted/rejected counts and best observed accuracy. This summary is hidden on the active driving surface and is a measurement only, not a route score or calibration pass.

1. **Stationary baseline:** wait for a fresh direct location fix in an open-sky location. Record the app-reported confidence, fusion mode and horizontal accuracy where shown.
2. Confirm the AI route-priority question and rating controls are no longer on screen after starting navigation. Do not answer questions or rate routes while the vehicle is moving.
3. **Open-sky segment:** follow the planned route without interacting with the device. Record missed/late maneuvers, route deviation prompts, Safety Hold events and whether progress resumes only after trusted observations.
4. **Constrained reception segment:** if the route naturally includes an urban canyon or covered section, record loss/recovery behavior. Do not create a risky route to force signal loss; Safety Hold is an acceptable outcome when trusted positioning is unavailable.
5. **Reroute check:** at a safe, preselected stopping point, use the passenger-operated test flow to verify reroute behavior. Never make an abrupt turn or stop to provoke a reroute.
6. **Repeatability pass:** repeat a comparable segment once if time and conditions allow. Keep the route and test conditions the same; do not compare results from different candidate SHAs.
7. **Post-drive check:** record final accepted/rejected calibration counters, best observed accuracy, route/session evidence identifiers, failures, app restarts and any observed safety holds. Treat these values as measurements, not as automatic calibration success.

## Evidence and go/no-go

Keep detailed evidence on the test device or an approved local storage location. Export only the minimum necessary proof after the drive; review it for coordinates, personal data and secrets before sharing. Record candidate SHA, APK SHA-256, device/OS, route label (not coordinates), time window, start/end calibration counters, observations and operator notes.

**Go** means all software gates passed on the exact frozen SHA, the field APK identity was verified, the route service and ADB reverse were ready, and preflight showed no safety blocker. It authorizes the supervised test only; it is not a release or calibration-pass claim.

**No-go** if any CI gate is missing or failing, hashes do not match, the field APK is stale, the route service is not ready, route acquisition uses the bootstrap route, location is untrusted, the device overheats/restarts, or safe operation is uncertain. Preserve the evidence locally and correct the cause before scheduling a new run.

## Result record template

- Date/time and test operator:
- Candidate SHA:
- Field APK SHA-256:
- Device / Android version:
- Route label and conditions (no coordinates):
- Route service `/ready` and ADB reverse verified:
- Starting and ending accepted/rejected sample counts:
- Best reported horizontal accuracy before/after:
- Safety Hold / stale observation / reroute observations:
- Crash, restart, cancellation or thermal events:
- Local evidence location and review status:
- Outcome: NOT RUN / PASS / FAIL, with reason:

A physical-device record is valid only for the exact candidate and APK hashes tested. Never fill in measurements from CI or from another device run.
