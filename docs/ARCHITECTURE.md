# Foundation 0.2 Architecture

The initial executable slice is deliberately small but follows the final boundaries:

1. `schemas/` — versioned cross-language contracts.
2. `core/` — platform-neutral C++20 routing intelligence.
3. `tests/` — deterministic executable tests.
4. `scripts/` — build and policy checks.

The first Cost Engine already distinguishes feasibility from soft preference costs,
uses seconds-equivalent as the explainable cost unit, bounds positive preference
bonuses using the comfort budget, and preserves per-rule contributions.

Next integration boundary: `IRoutingEngine` + a Valhalla implementation.
