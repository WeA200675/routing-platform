# Training Platform Dashboard

The dashboard is the operational interface and the conversation surface for the training system.

## Areas

### Overview
Active release and safety mode, learning phase, worker heartbeat, route-quality trend, alerts, storage and quota headroom.

### Learning activity
Current investigation, evidence, questions, candidates in simulation/shadow/canary, completed and rejected experiments, expected gain versus resource cost.

### Navigation quality
Route-time error, maneuver correctness, voice timing, GPS stability, fallback rate, source freshness, confidence intervals and sample counts.

### Knowledge explorer
Searchable facts and patterns, evidence chain, source health, expiry, retention, contradictions and affected route classes.

### Conversation
Text and voice input, route context, structured action cards, evidence-backed explanations, confirmation for impactful commands and decision history.

### Governance
Release comparison, regression failures, approvals, rejection, rollback, immutable audit history and policy controls.

## Visual rules

- Every chart shows period, sample count and version.
- Critical failures use explicit severity, not color alone.
- Incompatible metrics are not combined.
- Production state is always visible.
- Safe mode and stale data are prominent.
- Partial or delayed data is represented honestly.

The dashboard must never imply that a candidate is active merely because it exists.
