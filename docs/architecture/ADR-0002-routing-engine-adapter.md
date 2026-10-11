# ADR-0002: Routing engine boundary

Status: accepted for Foundation 0.2

## Decision

The platform owns a small `IRoutingEngine` contract in the platform-neutral C++ core. Valhalla is
implemented behind an adapter and must not leak Valhalla-specific types into Rule Engine, Cost
Engine, Candidate Generation, Navigation Core or tests.

Foundation 0.2 deliberately provides a buildable fallback adapter when libvalhalla is absent. This
keeps the core and CI usable on developer machines without silently replacing Valhalla with a
proprietary web service.

## Why

- Valhalla remains the preferred open-source routing backend.
- We retain the option to compare another open-source engine later.
- Frozen regression fixtures can target the platform contract instead of third-party internals.
- Mobile and server builds can use different linkage/deployment strategies without changing core
  routing semantics.

## Valhalla integration path

Valhalla's current build documentation recommends consuming `libvalhalla` through `pkg-config` /
`pkg_check_modules`. The first linked implementation should therefore be enabled by an explicit
CMake option and discovered through pkg-config rather than vendoring an unpinned source snapshot.

The linked adapter will translate platform `RouteRequest` values into Valhalla requests and map the
returned route(s) back into platform `RoutePath` values. Custom edge costing remains a later
milestone because it requires a deliberately version-pinned Valhalla integration rather than an
unstable dependency on internal headers.
