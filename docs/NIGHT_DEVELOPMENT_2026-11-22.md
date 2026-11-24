# R1 P0 APK provenance handoff — 2026-11-22

## Verified starting state
- PR #55 open and draft, head `638d46561b66fe972e9fcc7e8c2829c47cb485a1` at start. DO NOT MERGE OR DEPLOY.
- All five PR-triggered GitHub Actions runs for that exact SHA completed successfully: Android CI 44954628107, Android/core 44954628147, backend safety 44954628031, storage 44954628223, core 44954628281.
- Read R1/R2 plans and `docs/R1_P0_VERIFIED_GATE_2026-11-21.md`; physical device and APK extraction remained open.

## Change
- `6da69106c50dd84ddd7205a1ad142759f5b28562`: Android CI now requires **exactly one** debug APK, generates SHA256SUMS.txt **in the APK's directory** with the APK basename (rather than a build-root-relative path), immediately checks it with `sha256sum --check --strict`, and bundles `SOURCE_COMMIT.txt` containing `GITHUB_SHA`. Upload paths include both alongside the APK.
- This corrects a packaging/provenance gap: previously the checksum file referenced a path not necessarily present after GitHub artifact directory flattening. It does not alter Android runtime code.
- No existing tests disabled.

## Gate and validation
- The workflow change is committed; **the new SHA's GitHub CI must be inspected before treating it as green**. No local Android SDK/device execution claimed.
- On download: compare `SOURCE_COMMIT.txt` to the workflow run's head SHA; from the extracted APK directory run `sha256sum --check --strict SHA256SUMS.txt`. Record the actual APK digest. The GitHub ZIP artifact digest is *not* the APK digest.
- Previously verified ZIP artifact 14673303448 from run 44825271650 (code `d23609b`) remains a candidate; prior fallback artifact 14363468060 (commit `2d1653c2`) is preserved.
- **NO-GO for road use** until installation, offline tile persistence/Valhalla, maneuver direction, voice, GPS, Wi-Fi loss and Pixel 10 Pro physical checks pass.

## Next eight prioritized packages
1. P0: inspect CI for `6da69106`; fix any failed checksum/upload steps without weakening tests.
2. P0: obtain exact new artifact; verify provenance and extracted APK SHA256; record package/version.
3. P0: safe device install/launch smoke with existing app/data backup.
4. P0: offline DACH tile import/restart, local Valhalla route start without network.
5. P0: deterministic left/right/motorway exit shape-index fixtures and voice stage tests.
6. P0: GPS jitter, WLAN loss, local fallback, reroute and lifecycle tests.
7. P1: opt-in, advisory-only KI/training, idempotent sync and fail-closed Journey M1–M4.
8. P2: feature-flagged offline POI break advisor, genuine same-map Valhalla via-POI detour <=600s, consent/cooldown/UI.

## Rollback and boundaries
If packaging regresses, revert only commit `6da69106` after confirming branch HEAD. Do not force-reset or overwrite user data. No main merge, Oracle access/deployment, cost, secrets, APK publication or physical driving occurred.
