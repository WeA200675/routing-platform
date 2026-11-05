# AI Performance Learning Loop

Status: Implemented as the first performance-control slice

## Objective

The training system must improve over time across routing quality, voice correctness, stability, latency and resource efficiency. A version is not accepted because one average score improved. Critical regressions block release.

## Performance vector

Every evaluation records:

- route quality;
- travel-time prediction error;
- maneuver and left/right correctness;
- voice timing;
- GPS stability;
- fallback rate;
- response latency;
- CPU and memory use;
- network and storage use;
- successful problem resolution;
- safety-gate result.

A composite score is useful for trends, but release gates inspect each critical dimension separately.

## Scheduled optimization curriculum

The optimizer cycles through these tracks in a fixed round-robin order:

1. data quality and freshness;
2. route choice and travel-time accuracy;
3. maneuver and voice timing;
4. GPS stability and jitter;
5. response latency;
6. storage, network and CPU efficiency;
7. fallback and degraded-mode behavior;
8. full-system replay and stability.

Each cycle has a baseline, candidate, replay set, resource budget and release decision. A cycle is skipped when its expected gain is lower than its cost.

## Regression protocol

When a metric crosses its regression threshold:

1. freeze the candidate;
2. capture the failing evidence and environment;
3. create a permanent replay case;
4. create a targeted repair-training task;
5. compare the repair against the last stable release;
6. run shadow mode;
7. release only after all critical gates pass.

A regression case remains in the permanent evaluation catalog.

## Release gates

A candidate can move from shadow to canary only if:

- every critical safety test passes;
- no critical metric is worse than the active release;
- the composite score is not lower;
- latency and resource budgets are respected;
- the result is reproducible;
- rollback metadata is complete.

If evidence is insufficient, the active release remains unchanged.

## Dashboard curve

The dashboard plots:

- active-release performance;
- candidate performance;
- reference minimum;
- confidence band;
- regression markers;
- optimization-track markers;
- sample count and evaluation window.

A downward movement is visible and linked to its repair task. The graph must never smooth away a critical regression.
