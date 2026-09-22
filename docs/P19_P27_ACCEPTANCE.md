# P19–P27 Acceptance — Production Maturity

P19–P27 extend the unified P-series without weakening P1–P18 invariants. Routing, safety and security decisions remain deterministic; AI remains advisory. Coordinates, geometry, maneuvers, traffic, ETA and progress are never invented.

| Milestone | Required implementation invariant |
|---|---|
| P19 Production Hardening & Observability | Health and diagnostic state is bounded, deterministic and privacy-safe. Diagnostic failure cannot alter navigation semantics or expose sensitive security evidence. |
| P20 Offline-First Routing & Recovery | Offline routing/data capabilities are explicit and versioned. Missing, corrupt or stale data fails closed and cannot be represented as fresh authoritative routing data. |
| P21 Advanced Navigation & Re-Routing | Route progress, deviation and reroute remain session-scoped and gated by trusted fresh observations. Stale callbacks cannot replace the current route. |
| P22 iOS Production Implementation | Apple-authorized adapters implement shared measurement, secure-storage and authenticated-presentation contracts. Shared semantics contain no Android/manufacturer assumptions. |
| P23 Android/iOS Feature Parity | A common fixture corpus exercises equivalent capability and observation inputs on both adapters and requires identical semantic admission/rejection outcomes. |
| P24 Performance, Battery & Resources | Runtime work has explicit resource bounds. Pressure, cancellation and degraded operation fail closed without bypassing routing, privacy or security gates. |
| P25 Privacy, Security & Supply Chain | Sensitive evidence remains local/encrypted/bounded; release dependencies and actions are immutable/pinned and provenance is verifiable. |
| P26 Release Automation, Signing & Distribution | Release packaging binds an immutable source SHA to reviewed inputs, SBOM, hashes, manifests and platform artifacts. Production signing credentials are never stored in repository evidence. |
| P27 Production Validation & GA Readiness | One immutable candidate passes all automatable Core/platform/release gates. Physical Android and iOS acceptance is recorded separately and is never inferred or fabricated. |

## Cross-platform invariants

Shared code owns semantic admission, freshness, lifecycle and safety decisions. Platform adapters own OS permission/capability checks, authorized observations, secure-key storage, authentication and presentation. Adapters may report a capability only when they can actually provide the corresponding authoritative input.

## Evidence integrity

A green CI run proves only the steps executed by that run. Emulator, JVM or host tests are not physical-device evidence. Android evidence is not iOS evidence. An installable test-signed package is not proof of production-store signing or publication.

## Promotion gates

P19–P21 may be developed while P18 physical acceptance is pending, but P22–P27 cannot be marked accepted from Android-only CI. P27 requires the exact candidate SHA, complete automated evidence and separately captured physical-platform acceptance.

## P19 implementation evidence

P19's first production observability boundary is implemented by `NavigationAdmissionHealth` and `NavigationRuntimeHealth`. Both are deliberately bounded semantic projections. Runtime health excludes route/position data, horizontal accuracy values, raw sensor/radio evidence, native failure messages and security evidence. Constructor invariants reject internally contradictory health claims such as an accepted native update without an attempted update, or automatic progress while the pipeline is stopped.

P19 automated acceptance requires Core CI and Android CI to pass on the same immutable candidate SHA containing these contracts and tests. The P1–P27 candidate workflow is additional release evidence; it does not convert CI into physical-device evidence.

## P20 implementation evidence

P20 defines a platform-neutral `OfflineRoutingAdmission` and deterministic `OfflineRoutingRecovery`. Dataset identity and version must be explicit, integrity must already be verified, timestamps must be monotonic/non-future and age must remain within a reviewed budget. Recovery publishes dataset identity only after successful admission; rejected data cannot survive as an active offline source.

Automated tests cover missing/blank identity, failed integrity, future timestamps, stale data, invalid clock/budget input, the inclusive freshness boundary and recovery identity clearing. CI proves only these host/JVM contracts; it does not prove completeness or freshness of real map packages on a physical device.

## P21 implementation evidence

P21 hardens the existing sustained-evidence reroute engine by binding accumulated reroute evidence to the active navigation session. A session transition resets candidate timestamps, sample counts and attempt history before new-session evidence can qualify. Blank explicit session identity fails closed. The lifecycle controller supplies the current snapshot session to the decision engine, while its existing generation and expected-session checks continue to discard stale asynchronous route responses.

Existing reroute admission still requires sustained `HeldOffRoute` evidence, trusted position confidence, non-dead-reckoning fusion, strictly increasing monotonic timestamps and an attempt interval. Via-point rerouting and incomparable periodic route replacement remain deliberately unavailable rather than guessed.

P21 automated acceptance requires the Android unit/lint/build gate on the immutable SHA containing the session-isolation tests. Core CI is complementary. Physical-drive behavior remains separate device evidence and is not inferred from JVM/CI results.

## P22 implementation evidence

P22 now has a buildable Swift package, fail-closed navigation admission types, XCTest coverage and an Apple Core Location capability adapter. The adapter maps denied/not-determined authorization to unavailable direct observation and requires full-accuracy authorization before calibration can be admitted. A dedicated macOS GitHub Actions gate builds and tests the Swift package. This proves source/build contract behavior only; it is not an iPhone application, device run, secure-storage implementation or physical iOS acceptance.

## P23 implementation evidence

P23 starts a common platform-neutral admission fixture corpus at `platform/shared/parity/navigation-admission-fixtures.csv`. The corpus records identical capability and observation cases that Android and iOS parity tests must consume. P23 is not accepted until both platform test suites consume the same fixtures and the iOS CI gate is green on the immutable candidate SHA.

## P24 implementation evidence

P24 introduces explicit navigation resource budgets and a fail-closed governor for bounded buffered samples and pending work. Boundary values are admitted; exhausted budgets and malformed negative counters are rejected by unit tests. This is an enforceable semantic pressure contract, not a claim about measured physical-device battery life. CPU, memory, wakeup and battery measurements still require platform/device evidence before P24 can be fully accepted.

## P25 implementation evidence

P25 retains the existing repository-wide immutable GitHub Action pin verifier as an explicit production-candidate gate. Candidate generation already binds pinned runtime inputs to source digests, SBOM and release evidence, while release checks reject unsafe manifest/network/diagnostic exposure. Production secrets and signing credentials remain outside repository evidence. P25 acceptance requires these gates to pass on the immutable candidate SHA; repository branch-protection administration is separate evidence.
