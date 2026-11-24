# R1 P0 verified gate snapshot — 2026-11-21 23:00 Europe/Berlin

## Scope and immutable evidence
- Branch: `fix/verified-training-pipeline`. Compared `d23609b2568cbb519a8e4e0f62d578b76984ae33...fix/verified-training-pipeline`: **identical**, zero commits ahead/behind at inspection.
- PR #55: https://github.com/WeA200675/routing-platform/pull/55 ; remains explicitly marked DO NOT MERGE OR DEPLOY in PR description. No merge performed.
- Previous handoff: `docs/RC_GATE_NO_GO_2026-11-20.md` (read). Previous test correction `c73a404c740c4d4c51272fc0c1b3a63460598e42`.
- R1 and R2 planning documents read. R1 P0 remains ahead of Journey/R2/R3 features.

## CI verified through GitHub Actions API
For **exact commit `d23609b2568cbb519a8e4e0f62d578b76984ae33`**:
- Android CI: success, https://github.com/WeA200675/routing-platform/actions/runs/44825271650
- Android and core verification: success, https://github.com/WeA200675/routing-platform/actions/runs/44825271753
- Core CI: success, https://github.com/WeA200675/routing-platform/actions/runs/44825271628
- Training backend safety checks: success, https://github.com/WeA200675/routing-platform/actions/runs/44825271651
- local-storage-stack: success, https://github.com/WeA200675/routing-platform/actions/runs/44825271646

The prior correction commit `c73a404` has a successful Android-and-core verification run (44825218479); its standalone Android/Core runs were cancelled, superseded by the fully green documentation HEAD above. No tests were disabled in this snapshot.

## Newly confirmed candidate artifact (not field-approved)
- Android CI run: https://github.com/WeA200675/routing-platform/actions/runs/44825271650
- Artifact name: `routing-platform-debug-apk`
- Artifact ID: `14673303448`
- Size: `53,327,502` bytes
- Created UTC: `2026-11-20T22:04:25Z`; expiry UTC: `2026-12-04T22:04:22Z`
- GitHub-reported **ZIP artifact** digest: `sha256:c69a050cfc85b3129819fafd897d4ec930e9b610a67b8f4233bfb9341a433153`
- **Not** the extracted APK SHA256. APK bytes, package metadata, installation, map availability and physical Pixel 10 Pro test remain unverified.
- Earlier fallback remains: commit `2d1653c2c0e49435ea9d1ec7e3c3547386768cf2`, artifact `14363468060` from https://github.com/WeA200675/routing-platform/actions/runs/44161703418. Do not overwrite existing device data without backup.

## Release decision: NO-GO for road use
All five CI gates are green, and a same-HEAD APK ZIP artifact exists, but offline Valhalla route start, tile import/persistence, correct left/right and exits, real voice timing, GPS jitter, Wi-Fi loss/fallback and Pixel 10 Pro installation/driving have **no new device evidence**. Do not claim those gates passed. No APK publication, Oracle deployment, main merge, data deletion or cost incurred.

## Safe next eight-part execution plan
1. Download the **exact** artifact 14673303448; verify ZIP digest, extract APK and compute APK SHA256; record package/version/commit.
2. Run safe Android installation/start smoke test on Pixel 10 Pro with backups and explicit user-controlled test setup; preserve fallback.
3. Validate offline tile import, restart, route start, no-network Valhalla availability and missing-tile failure behavior.
4. Add versioned maneuver fixtures for left/right, motorway exits, shape indices and deterministic JNI-to-voice mapping; run focused and full tests.
5. Verify Early/Prepare/Now timing, GPS spikes, TTS queue, route-stage transitions and reroute using deterministic tests and physical test evidence.
6. Verify Wi-Fi loss, local fallback, heading smoothing and app lifecycle; block promotion on any regression.
7. Only after P0: opt-in training/LLM advisory-only, SQLite/PostgreSQL sync, authenticated receipts and fail-closed Journey M1–M4.
8. Only after P0: R2/R3 break assistant offline POIs, same-version Valhalla via-POI <=600s, explicit confirmation, cooldown, feature flag and tests.

## Rollback
Keep fallback artifact and reference commit untouched. No destructive operations. If a subsequent patch breaks P0 gates, revert only the patch on the development branch after inspecting current HEAD; never force-reset main. Physical road tests remain open.
