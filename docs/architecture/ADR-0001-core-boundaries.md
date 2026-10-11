# ADR-0001: Keep routing intelligence independent of presentation and routing vendor

Status: Accepted for Foundation 0.1

## Decision

The shared C++ core owns Street Model interpretation, Rule Engine behavior, Cost Engine behavior,
Candidate ranking, explanation, navigation state and test semantics.

A concrete path-search implementation (initially Valhalla) is accessed through an adapter boundary.
Android, Android Auto, iOS, CarPlay and Ford/SYNC are presentation/integration adapters and must not
contain routing policy.

## Consequences

- routing behavior can be tested without a phone UI;
- Valhalla can later be compared against another engine;
- online and offline routing can share the same rule/cost semantics;
- vehicle integrations cannot silently change route policy;
- more upfront interface discipline is required.
