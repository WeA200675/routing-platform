# Recovery: 40 logical steps to a usable navigation app

Branch: `fix/verified-training-pipeline`. No merge to `main`, public deployment or APK release without explicit approval and passing gates. **A commit is not a passing test.** Priority: restore offline navigation before enabling optional intelligence.

## P0 – recover a reliable Android navigation baseline (1–8)
1. [ ] Record HEAD, green/red CI matrix and reproducible build provenance.
2. [ ] Freeze the last known usable APK and SHA-256 as rollback.
3. [ ] Reproduce clean Android debug build on supported toolchain (Android CI recovery branch trigger enabled; awaiting result).
4. [ ] Resolve all Kotlin/JVM unit-test failures; attach exact logs.
5. [ ] Restore offline map import and persisted map selection.
6. [ ] Verify Valhalla route creation with Wi-Fi disconnected.
7. [ ] Verify route preview, start, progress, arrival and restart.
8. [ ] Run device smoke test; no crashes, route loss or false online dependency.

## P1 – backend boundaries and connection resilience (9–16)
9. [x] Provide loopback-only Journey status gateway with versioned JSON.
10. [x] Report degraded/not-ready until evidence pipeline is verified.
11. [x] Add Android HTTPS-only status client and offline fallback.
12. [x] Add bounded HTTP gateway regression tests.
13. [x] Include Journey files in CI push path filters and test command.
14. [ ] Wire client into application lifecycle via non-blocking background dispatcher (standalone monitor committed; lifecycle wiring outstanding).
15. [ ] Add user-configurable HTTPS backend endpoint and certificate validation.
16. [ ] Add exponential backoff, bounded queue, jitter and connectivity state (basic bounded backoff and single-flight monitor committed; jitter and queue outstanding).

## P2 – Valhalla, POI and safety evidence (17–24)
17. [x] Compare full-route duration, not geographic detour distance.
18. [x] Reject unmatched map versions and unverified graph identity.
19. [x] Require WC, access, direction and route-order evidence.
20. [x] Bound and SHA-256 pin the offline POI index.
21. [ ] Expose actual graph identity from trusted Valhalla tile provenance.
22. [ ] Publish audited POI corpus with independent review and provenance.
23. [ ] Verify stop placement with existing via-points and return routes.
24. [ ] Run real Valhalla/POI integration tests; fail closed on mismatch.

## P3 – reliable backend service and training (25–32)
25. [ ] Expose authenticated HTTPS API via reverse proxy; no public debug service.
26. [ ] Add versioned backend capability negotiation and schema compatibility.
27. [ ] Server-side evaluate verified candidates, not client-supplied trust booleans.
28. [ ] Add idempotent feedback ingest and bounded durable queue.
29. [ ] Add replay/quality gates before training promotes any route change.
30. [ ] Add metrics: request failures, timeouts, cache hit rate, latency, fallback.
31. [ ] Add offline-first retry with rate limiting and circuit breaker.
32. [ ] Document backup/restore, resource caps and health checks.

## P4 – end-to-end release and field acceptance (33–40)
33. [ ] Verify spoken turn direction against geometry (left/right).
34. [ ] Fix speech timing and deduplicate maneuver announcements.
35. [ ] Stabilize GPS jitter, map bearing and speed-dependent zoom.
36. [ ] Validate route recovery after WLAN/mobile handover and GPS loss.
37. [ ] Verify speed warnings and lane guidance against offline data.
38. [ ] Confirm route guidance works without cloud, training or Journey service.
39. [ ] Build signed or explicitly debug-labelled APK with SHA-256 and rollback (CI debug APK artifact enabled on recovery branch; await successful run and compute SHA-256).
40. [ ] Execute recorded road-test acceptance, triage blockers, then release gate.

## Evidence and fallback
- Checked items mean **code committed**, not proven end-to-end functionality.
- The gateway intentionally returns `recommendations_enabled=false`, `/ready=503` and `POST /api/journey/v1/recommend=503`. This is safe but not yet a live recommendation service.
- Android's `JourneyBackendClient` is currently a standalone client and **not yet wired to UI or routing lifecycle**.
- Local route service default 8787 conflicts with a training API commonly using the same port; assign separate ports explicitly.
- No speculative POI recommendation, no auto-reroute, no network requirement for core navigation.
- Next priority: P0 device/build recovery (steps 1–8) while integrating step 14 behind a disabled feature flag. Do not spend the next sprint polishing optional Journey UI while the base app is broken.

## Deadline recovery run
- Recovery branch now triggers `.github/workflows/android-ci.yml` on push. Its `verify` job runs Android unit tests, lint, release Kotlin compilation and `assembleDebug`; on success it uploads `routing-platform-debug-apk` for 14 days.
- Retrieve the APK from GitHub Actions run artifacts; do not claim availability before the job is successful and the artifact exists.
- Backend Journey service remains optional and deliberately degraded. The offline navigation smoke test is mandatory before driving.
