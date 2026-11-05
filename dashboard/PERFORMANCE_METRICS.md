# Performance Dashboard Metrics

The first dashboard slice must render the performance vector without hiding regressions.

## Panels

- overall curve: active release, candidate and reference minimum;
- optimization curriculum: current track and next scheduled track;
- regression queue: open repair tasks and permanent replay cases;
- quality vector: route, voice, GPS, fallback, latency and resources;
- release gate: every gate with evidence and sample count;
- work log: what the AI is currently evaluating and why.

## Chart contract

Every series includes release ID, track, time window, sample count and confidence metadata. Critical regression points are not averaged away or silently removed.

## States

- observing;
- optimizing;
- replaying;
- regression-repair;
- shadow;
- canary;
- released;
- rolled-back;
- blocked.
