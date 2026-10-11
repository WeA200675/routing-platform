# G8 – Advanced Routing Intelligence: Acceptance Contract

Status: normative acceptance contract established because the repository previously contained no G8 definition.

G8 adds deterministic routing intelligence on top of the existing route engine. AI may explain or collect intent but may not author routing facts.

## Definition of Done

1. Every advanced route preference maps to an exact routing-engine capability; unsupported properties fail closed rather than being approximated.
2. Alternative-route comparison uses engine-derived metrics only and preserves units/provenance.
3. Reroute decisions are deterministic, hysteresis-protected and regression-tested against oscillation.
4. Context inputs used for routing have freshness/confidence gates; stale or low-confidence context cannot silently change the route.
5. Route scoring/tie-breaking is deterministic for identical inputs and never depends on LLM output.
6. Offline/no-provider states have explicit safe behavior and no implicit network or AI fallback.
7. Privacy boundaries prevent route intelligence from sending Social-AI prompts, favorites or precise location to an undeclared service.
8. Unit, integration, regression and acceptance tests cover capability rejection, alternatives, reroute stability, stale context and privacy.
9. Core CI and Android CI are green on the final G8 SHA; signed candidate evidence is pinned and reproducible.
