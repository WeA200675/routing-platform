# P1–P9 Unified Production Milestones

This roadmap consolidates the historical G/H streams without rewriting their immutable commits or acceptance evidence. Existing G/H documents remain authoritative evidence for the work they describe.

| Milestone | Scope | Acceptance |
|---|---|---|
| P1 Foundation | Consolidate G/H evidence, deterministic contracts, build hygiene | Core + Android CI green on candidate |
| P2 Deterministic Routing | Exact provider capabilities, route/via integrity, no approximated properties | Unsupported capabilities fail closed |
| P3 Navigation Runtime | Lifecycle, progress, reroute, trusted observation | Stale/untrusted observations cannot move progress |
| P4 Local AI | Pinned local model/runtime, intent-only AI boundary | AI cannot invent coordinates or directly mutate navigation |
| P5 Context & Calibration | Fresh context and manufacturer-neutral local calibration | Versioned/resettable/freshness-gated; no route mutation |
| P6 Security & Privacy | Observer → policy → enforcement, encrypted evidence, authenticated diagnostics | Defensive-only; no scan/hack-back; bounded local evidence |
| P7 Production Reliability | Restart/corruption/resource/concurrency hardening | Fail closed under malformed state and bounded resources |
| P8 Cross-Platform Contracts | Shared semantic contracts with platform adapters | Android has no manufacturer-specific policy; iOS can implement same contracts |
| P9 Production Release | One immutable candidate, SBOM/hashes/manifest gates, physical acceptance | All automated gates green on exact SHA, then one physical reference-device package |

## Non-negotiable invariants

Routing, safety and security enforcement are deterministic. AI is advisory/intent/correlation only. Coordinates, geometry, maneuvers, traffic, ETA and progress are never invented or silently approximated. Sensitive security evidence is local/encrypted/bounded and excluded from AI prompts and ordinary telemetry. Driving security never performs retaliation or distracting alarm escalation. Platform differences are implemented through capability adapters rather than manufacturer policy.

## Release completion

P1–P9 automated implementation is accepted only when Core CI, Android CI and the unified release-candidate workflow all succeed on the same immutable SHA. P9 as a whole additionally requires the documented physical-device run; hardware evidence must never be fabricated.

## P10–P18

P10 Runtime Resilience → P11 Navigation Quality → P12 Routing Intelligence → P13 Local AI 2.0 → P14 Privacy & Security Hardening → P15 Shared Platform Core → P16 iOS Runtime Boundary → P17 Cross-Platform Parity → P18 Production Release. Their normative acceptance contract is `docs/P10_P18_ACCEPTANCE.md`.

## P19–P27 — Production Maturity

P19–P27 start only after the P18 automatable gates are green. Hardware-dependent acceptance remains explicit and must never be inferred from CI.

| Milestone | Scope | Acceptance |
|---|---|---|
| P19 Production Hardening & Observability | Deterministic health/diagnostic signals, bounded local telemetry and recovery evidence | Failures are observable without leaking sensitive evidence; malformed diagnostics fail closed |
| P20 Offline-First Routing & Recovery | Explicit offline capability, cache/data-version handling and deterministic recovery | Missing/stale offline data is unavailable rather than silently approximated |
| P21 Advanced Navigation & Re-Routing | Harden reroute, deviation, tunnel/degraded-GNSS and route-lifecycle behavior | Only trusted fresh observations may advance or replace navigation state |
| P22 iOS Production Implementation | Implement Apple-authorized adapters for the shared P15/P16 contracts | iOS build plus adapter contract tests; no Android/manufacturer assumptions in shared semantics |
| P23 Android/iOS Feature Parity | Run common fixtures against both platform adapters | Equivalent authoritative inputs produce identical fail-closed semantic outcomes |
| P24 Performance, Battery & Resources | Bound CPU, memory, storage, wakeups and sensor/runtime work | Resource budgets are measured; exhaustion/degradation cannot bypass safety gates |
| P25 Privacy, Security & Supply Chain | Harden local evidence, authentication, dependency provenance and release inputs | Security/privacy/supply-chain gates are deterministic, bounded and auditable |
| P26 Release Automation, Signing & Distribution | Reproducible platform packaging, production signing boundary and immutable release evidence | Exact candidate SHA is bound to hashes/SBOM/manifests and platform packages |
| P27 Production Validation & GA Readiness | Final cross-platform regression, device acceptance and release-readiness evidence | All automatable gates green on one immutable SHA plus explicit Android/iOS physical acceptance |

Normative acceptance details are defined in `docs/P19_P27_ACCEPTANCE.md`.
