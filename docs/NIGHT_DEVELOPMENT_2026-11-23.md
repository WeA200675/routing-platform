# R1 P0 night handoff — 2026-11-23

## Baseline and verified incident
- PR #55 is open, draft, unmerged, targeting main; pre-fix head `ec59589fa1da2468c5362a510eb5f4d080750ecb`.
- At that SHA, Android/core verification run 45252146189, Core CI 45252146172, backend safety 45252145918 and storage 45252145892 passed. **Android CI run 45252146372 failed**, job 137964403476, at "Verify APK provenance and tamper-detection tests". The earlier Gradle test/lint/compile/assemble step and APK SHA256 generation passed.
- Exact failure in the CI log: `APK artifact verification FAILED: source commit does not match expected HEAD`. Root cause: workflow used `printf '%s\\n' "$GITHUB_SHA"`, which writes a literal backslash+n rather than a newline. The independent verifier correctly rejected the malformed source manifest; its tests passed (9 tests). APK upload was skipped by fail-closed CI.
- Previous verified artifact ZIP 14775001842 (run 45074927996, head 558a94f2deb4b9dcf6b29d8615d8dc71b189214d), size 53,327,671 bytes, ZIP SHA256 e66d6755ac974b00d9316765afc1eff9beb327e6c22478d432cac1c124ebf091. **This is not the extracted APK SHA256.** Artifact expiry 2026-12-06. Previously documented fallback ZIP 14673303448 (run 44825271650) remains a separate older candidate.

## Applied change
- Commit `e83fc80db03af3451e27b67434250f06cc944087`: change the manifest writer to `printf '%s\n' "$GITHUB_SHA"`. Keep independent source-SHA and APK-digest validation intact; do not bypass or disable the verifier.
- This is a packaging/CI fix only, **not a navigation-runtime fix**.
- New CI for this commit must finish before claiming a green gate. No physical device, local Android SDK, or extracted APK checksum validation performed in this run.

## Release decision and rollback
**NO-GO for physical road navigation**: actual APK install/launch, persistent DACH offline tiles, local Valhalla routing, left/right/motorway exits, voice timing, GPS stability, Wi-Fi interruption and recovery remain unverified on Pixel 10 Pro. The 2026-11-20 and 2026-11-21 targets have elapsed; do not backdate release success.
Rollback if the new manifest step regresses: revert only `e83fc80` after inspecting current branch HEAD; preserve existing artifacts and all user data. No merge, deployment, APK publication, secrets or spending.

## Next eight prioritized work packages
1. P0: inspect all CI jobs on `e83fc80`; repair any new provenance failure with targeted tests, without weakening gates.
2. P0: retrieve artifact from the passing exact-SHA run; verify `SOURCE_COMMIT.txt` and the **extracted APK** SHA256, version/package and installation prerequisites; keep ZIP digest distinct.
3. P0: reproduce safe launch on Pixel 10 Pro without overwriting existing data; record app version, crash logs and rollback procedure.
4. P0: offline DACH tile import, restart persistence, local Valhalla route-start and airplane-mode smoke tests.
5. P0: deterministic left/right and motorway exit shape-index regression fixtures, including prepare/now voice timing and GPS jitter.
6. P0: WLAN-loss mid-route, local fallback, reroute, lifecycle and battery/performance safety tests.
7. P1: advisory-only KI/LLM, real evidence-based training, idempotent sync and fail-closed Journey M1–M4.
8. P2: gated offline POI WC/rest assistant with both eligibility thresholds, legal-direction via-POI Valhalla detour <=600 s, consent, cooldown and UI; do not infer live availability.

R1 remains ahead of R2/R3. No synthetic training quality metrics or unverified release claims.
