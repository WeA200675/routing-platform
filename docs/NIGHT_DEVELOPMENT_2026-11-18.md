# Night engineering handoff – 2026-11-18

## Scope and baseline
Branch `fix/verified-training-pipeline`; PR #55 remains a draft with explicit DO NOT MERGE/DEPLOY. Reviewed `docs/RECOVERY_RELEASE_R1_2026-11-17.md`, `docs/AUTONOMOUS_ENGINEERING_R2_2026-11-17.md`, and `docs/NIGHT_DEVELOPMENT_2026-11-17.md`. Previous handoff identifies `2d1653c2c0e49435ea9d1ec7e3c3547386768cf2` as a verified five-workflow baseline, with an Android debug artifact, but no APK-byte SHA256 or device attestation. GitHub commit search did not reliably enumerate the branch HEAD; commits below are confirmed by branch-targeted writes, not by an independent ref HEAD query.

## P0 fix and regression
- `8bbe0ea7c7351109f7590249bc895c9287a992f6`: `NavigationVoicePlaybackRuntime.present` no longer calls `speaker.clearPending()` when `NavigationVoicePresentation.cue` returns null because distance jitter moved outside the active announcement window. The previous behavior could cancel an already queued prepare/now announcement and, because its key had been marked delivered, never replay it. Session change, disabled voice and arrival still cancel explicitly.
- `7b9ef87fc3ab460594abcee5d1accfd1c84d9c88`: adds a regression with a queued TTS speaker, 180m prepare cue, 230m jitter and return to 180m. Asserts no cancellation/replay on jitter and cancellation on voice disable.

## Evidence
The five GitHub workflows for the first code commit were observed **in progress**, not green, during this run. The test commit initially returned no associated workflow runs; retry CI verification before any promotion. Neither Gradle nor a Pixel 10 Pro physical drive test was executed locally. No new APK artifact or SHA256 was verified in this run. No claim of voice timing correctness on device.

## Remaining release blockers
P0: CI for final test commit, full APK installation, offline DACH tile import/restart, actual Valhalla left/right maneuvers, GPS jitter/heading, Wi-Fi disconnect and routing fallback, voice scheduling across maneuver boundaries. P1: authenticated sync, Journey POI attestation and true original/via route evidence; R2/R3 may not displace P0. PR #55 remains draft. No Oracle access, deploy, main merge, secret handling, destructive operation or cost.

## Rollback
Revert the two commits individually if tests expose regressions; retain previously verified `2d1653c2...` debug baseline and data untouched. The voice fix is isolated to playback coordination.

## Next eight-part engineering packet
1. Confirm exact branch HEAD, PR #55 and five-workflow CI; diagnose logs and repair failures before promotion.
2. Obtain the Android debug artifact and calculate SHA256 from downloaded bytes; record build variant, commit and artifact expiry.
3. Validate offline tile import/persistence and restart without network using deterministic tests.
4. Add versioned Valhalla left/right, exit, roundabout and shape-index regression fixtures.
5. Validate voice prepare/now and cancellation on actual maneuver advancement; no one-maneuver-late speech.
6. Exercise GPS jitter/heading and Wi-Fi interruption/recovery with fail-safe local routing.
7. Only after P0 green, implement advisory-only AI, idempotent sync and M1-M4 Journey evidence gates.
8. Publish Friday 20 Nov go/no-go with install/rollback instructions and prioritized defects; physical Pixel 10 Pro tests remain mandatory and explicitly unverified.

Target: Friday 2026-11-20 RC gate; Saturday 2026-11-21 device test candidate, otherwise NO-GO with verified fallback.
