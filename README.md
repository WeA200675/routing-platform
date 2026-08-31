# Routing Platform — Foundation 0.2

Open-source foundation for a highly configurable navigation/routing platform.

## Foundation principles

- first-party source code is Apache-2.0;
- required Routing Core dependencies must be open source or public domain;
- OpenStreetMap data licensing (ODbL) is tracked separately from source-code licensing;
- proprietary vehicle/phone runtimes may be targeted only through isolated, open-source adapters;
- the Routing Core must build and test without Android Auto, CarPlay or Ford/SYNC SDKs;
- no proprietary routing service is used as a fallback when an open-source backend is unavailable.

## Implemented through 0.2

- C++20 platform-neutral core;
- first Street Segment / Vehicle / universal Rule subset;
- explainable Cost Engine using seconds-equivalent;
- hard exclusion before soft-cost calculation;
- bounded preference bonuses through a comfort budget;
- cost-math helpers for nonlinear exposure, shortcut thresholds and comfort budgets;
- versioned Protocol Buffer schema files covering street data, rules, profiles, vehicles, cost
  vectors, candidates, navigation, feedback and regression tests;
- deterministic federal-road-vs-serpentine smoke test;
- **new:** platform-owned `IRoutingEngine` contract;
- **new:** route request/path/result boundary independent of third-party engine types;
- **new:** buildable Valhalla adapter skeleton with explicit `VALHALLA_NOT_LINKED` fallback;
- **new:** routing-engine contract and adapter fallback tests;
- **new:** ADR documenting the Valhalla integration boundary.

## Build and test

```bash
./scripts/check.sh
```

The default build does not require Valhalla to be installed. This is deliberate: Foundation 0.2
establishes and tests the dependency boundary first. `ROUTING_PLATFORM_WITH_VALHALLA=ON` is blocked
until the next milestone pins and integration-tests the libvalhalla API instead of coupling our core
to a moving third-party implementation.

## Open-source dependency policy

Valhalla remains the preferred routing backend and is MIT-licensed. The platform consumes it only
behind the open `IRoutingEngine` adapter. No proprietary routing backend is allowed as an implicit
fallback.

See `dependencies/oss-dependencies.toml`, `THIRD_PARTY.md` and `docs/OPEN_SOURCE_POLICY.md`.

## Next milestone

`Foundation 0.3` pins a Valhalla release, builds/links libvalhalla in an integration environment,
loads a tiny frozen OSM-derived graph fixture, performs the first real route request, and converts
that result back into the platform-owned `RoutePath` representation.

## Licensing

- First-party source: Apache License 2.0 (`LICENSE`).
- Dependency inventory: `dependencies/oss-dependencies.toml` and `THIRD_PARTY.md`.
- Data/open-source policy: `docs/OPEN_SOURCE_POLICY.md`.
## Foundation 0.3

Foundation 0.3 establishes the first real in-process routing path.

Implemented:

- C++20 routing core
- open-source Valhalla 3.8.3 integration
- direct in-process routing through `valhalla::tyr::actor_t`
- OpenStreetMap routing tiles
- real-route integration test using a Liechtenstein fixture
- static Valhalla dependency linking under WSL
- Linux-local WSL build directory to avoid filesystem clock-skew issues
- Windows fallback build without Valhalla

Verified reference route:

- origin: 47.1410, 9.5209
- destination: 47.1660, 9.5100
- distance: approximately 3174 m
- duration: approximately 242.149 s
