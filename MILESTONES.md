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
