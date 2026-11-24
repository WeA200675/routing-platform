# R1 P0 night handoff — 2026-11-24

PR #55 open, draft, unmerged. Branch baseline 662521b42f7966b612ce82549824513f877c58b9. R1 and R2 plans and previous handoff reviewed.
All five workflows completed successfully: Android/core 45276159143, Android CI 45276159298, backend safety 45276159156, storage 45276159161, Core CI 45276159346. Android job 138048417069 passed test/lint/release compile, digest generation, provenance/tamper tests and upload.

## Independently verified APK
Downloaded artifact 14867536683 from run 45276159298; expires 2026-12-07 22:09:10 UTC. Independently hashed actual ZIP and APK bytes using Python hashlib and inspected ZIP members.
- ZIP 53,326,447 bytes; SHA256 11ade3a6d25bd6d92515928d2f970ddcabf0963cddd82554801e8b659eb44766, matches GitHub metadata.
- debug/app-debug.apk 104,913,746 bytes; SHA256 fad0457ce9ca6990f12daa7f20a446bd2c680ab469a6892da56513a580b6bffd, matches debug/SHA256SUMS.txt and CI log.
- debug/SOURCE_COMMIT.txt contains a2ab927a237d96510d4f966a817c31c0ee6b7980, NOT branch head. Job checkout log explicitly confirms this is the synthetic PR merge of branch head 662521b42f7966b612ce82549824513f877c58b9 into base f6ddaf292d78e612c1abe78f03941fabcbf21e36. This is expected PR CI behavior, not a merge into main. Do not label this a branch-only build.
- https://github.com/WeA200675/routing-platform/actions/runs/45276159298

## Decision
Verified build artifact, not road-release approval. Deadline 21 Nov elapsed. Package/version/signature, device installation, launch and all physical navigation gates remain open. No runtime code change this run; no new local Android tests. No publication, deployment, main merge, data deletion or spending. Previous fallback artifact 14775001842 (run 45074927996) remains unmodified, not independently device-verified. Preserve data; do not uninstall or downgrade blindly. Documentation-only rollback via revert of this commit if necessary.

## Next eight packages
1. P0 verify package/version/signature and exact checkout provenance; distinguish PR merge from branch-only candidate.
2. P0 Pixel launch smoke and crash evidence; preserve existing data and signing compatibility.
3. P0 DACH tile import, restart persistence, airplane-mode local Valhalla route start.
4. P0 deterministic left/right and motorway exit replay, shape-index and voice prepare/now regression.
5. P0 GPS jitter, heading, reroute and lifecycle regression.
6. P0 WLAN loss and local fallback, recovery without cloud, route continuity.
7. P1 advisory-only KI/LLM, evidence-based training, secure idempotent sync, Journey M1–M4 with existing policy and fail-closed routing evidence.
8. P2 offline POI search, >90 min AND >60 km, legal direction and actual <=600s via-POI detour, consent/cooldown/UI feature flag; no live availability claims.
R1 gates precede R2/R3. Physical tests remain open until observed.
