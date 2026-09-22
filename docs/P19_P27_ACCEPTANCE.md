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
