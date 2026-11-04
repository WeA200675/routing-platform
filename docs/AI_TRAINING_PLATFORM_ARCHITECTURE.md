# AI Training Platform Architecture

Status: Planned
Decision: Monorepo with isolated services and contracts

## Decision

Keep the training platform in this repository, but isolate it from the production routing core. The existing routing application remains the authoritative runtime. Training, dialogue, ingestion, evaluation, versioning and visual observability connect through versioned contracts.

## Target layout

~~~text
routing-platform/
├── core/                         # deterministic routing and cost engine
├── adapters/                     # Valhalla and other open adapters
├── schemas/proto/                # shared wire contracts
├── intelligence/                 # ingestion, knowledge, dialogue, learning, evaluation, policy, API
├── dashboard/                    # visual operations and conversation console
├── tests/                        # deterministic, integration and replay fixtures
├── docs/                         # architecture, ADRs and milestone plans
└── .github/workflows/            # reproducible CI and evaluation jobs
~~~

## Trust boundaries

1. The deterministic routing core calculates distances, times, costs and maneuvers.
2. Intelligence may propose facts, weights and explanations.
3. Release policy decides whether a candidate can affect production.
4. Dashboard displays evidence and issues auditable commands.
5. Production app consumes only released compatible artifacts.

The language model is never the source of truth for arithmetic, safety rules or route geometry.

## Learning lifecycle

~~~text
observe → normalize → validate → store evidence → form hypothesis
→ replay/simulate → regression gate → shadow mode → canary
→ release or rollback
~~~

Every candidate has a parent version, evidence, metrics, failure cases and rollback target.

## Dashboard

The dashboard shows active and candidate versions, worker activity, data freshness, source health, retention pressure, route-quality trends, regression failures, proposed changes, conversation, approvals, rollback history and resource headroom. Every chart exposes its time window, sample count and uncertainty.

## Runtime modes

- observe: collect and explain, no route changes;
- shadow: candidate runs beside production;
- canary: candidate affects a bounded scope;
- production: released candidate is active;
- safe: intelligence disabled; deterministic routing remains.

## Invariants

- Production always has a deterministic fallback.
- Candidate data cannot mutate released artifacts.
- No critical regression may be hidden by an average score.
- Every release is reversible.
- Licenses and privacy classification are recorded.
