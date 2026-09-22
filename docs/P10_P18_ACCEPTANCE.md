# P10–P18 Acceptance — Resilience to Cross-Platform Production

P10–P18 extend the unified P-series. Historical G/H and P1–P9 evidence remains immutable.

| Milestone | Required implementation invariant |
|---|---|
| P10 Runtime Resilience | Restart, stale callback, malformed persistence and resource-bound paths fail closed; no prior session becomes current implicitly. |
| P11 Navigation Quality | Progress and reroute remain gated by trusted direct/fresh observations; degraded GNSS/tunnel state cannot invent position. |
| P12 Routing Intelligence | Alternatives, vias and preferences use explicit provider capability contracts; unsupported properties are unavailable, never approximated. |
| P13 Local AI 2.0 | Local AI remains intent/advice only; generations are session/context scoped and stale output cannot mutate current navigation. |
| P14 Privacy & Security Hardening | Security Observer → Policy → Enforcement remains deterministic; encrypted evidence is bounded, authenticated and defensive-only. |
| P15 Shared Platform Core | Navigation/calibration/security semantics are platform-neutral contracts; platform adapters provide measurements/storage/authentication. |
| P16 iOS Runtime Boundary | iOS implements the same contracts through Apple-authorized adapters; no Android/manufacturer assumptions may enter shared semantics. |
| P17 Cross-Platform Parity | Contract fixtures define identical fail-closed outcomes across platform adapters for equivalent authoritative inputs. |
| P18 Production Release | One immutable candidate passes Core CI, Android CI, release manifest/privacy/supply-chain gates and emits signed installable evidence; platform hardware acceptance remains explicit. |

## Cross-platform contract

Shared code owns semantics, freshness, deterministic admission and safety decisions. Android/iOS adapters own OS permission checks, sensor observations, secure-key storage and authenticated local-admin presentation. Neither adapter may fabricate unavailable capabilities. Calibration records quality only and cannot alter route geometry, coordinates, maneuvers, traffic, ETA or accepted progress.

## Release gates

The P1-P18 production candidate must run unit tests, release lint/compile, pinned local-model/runtime evidence, SBOM/hash verification and structural merged-manifest security checks on the exact SHA. Active radio scanning/change permissions, exported diagnostics, cleartext release networking and app-data backup are release failures. Physical road/device observations are required only after all automatable gates are green and are never inferred from CI.
