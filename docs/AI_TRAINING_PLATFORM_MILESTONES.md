# AI Training Platform — Master Milestone Plan

The plan adds a measurable, dialogue-capable and continuously improving system without uncontrolled regressions.

## M0 — Repository and contract foundation

Add monorepo boundaries, ownership rules, versioned protobuf/API contracts, explicit runtime modes, reproducible CI and decision records.

Exit: routing core builds independently; intelligence can be absent; contract compatibility is checked.

## M1 — Evidence and event pipeline

Add route-session, GPS, maneuver, voice and feedback events; source provenance; normalization; deduplication; quarantine; retention and compaction; local buffering.

Exit: each event has schema version, source, timestamp and quality; ingestion failure cannot stop navigation.

## M2 — Training knowledge base

Add immutable evidence, confirmed facts, derived patterns, contradiction handling, searchable history, confidence, explanations and privacy-aware deletion.

Exit: facts trace to evidence and uncertain data cannot directly alter production.

## M3 — Dialogue and command channel

Add text/voice dialogue, context-aware questions, structured investigate/simulate/explain/approve/reject/rollback commands, confirmation and audit records.

Exit: ambiguity produces questions and commands are idempotent.

## M4 — Replay and evaluation engine

Add frozen route fixtures, deterministic arithmetic checks, regression suites for left/right, timing, GPS jitter, fallback and data gaps, multi-objective metrics and worst-case gates.

Exit: a candidate cannot pass by improving only the average; every failure becomes a reusable test.

## M5 — Shadow learning

Generate candidates from validated evidence, run beside production, compare causally where possible, and show evidence, confidence and resource cost.

Exit: candidates have no production side effects and insufficient evidence keeps the active version unchanged.

## M6 — Canary and rollback

Add bounded canary scope, health checks, release manifests, artifact hashes, automatic rollback and incident timelines.

Exit: degraded candidates are removed automatically and deterministic routing remains available.

## M7 — Visual operations console

Add responsive dashboard with quality charts, confidence bands, pipeline status, source/storage health, experiment comparison, knowledge explorer, agent work log, chat, approvals and rollback.

Exit: every metric has period, sample count and source; activity is understandable without raw logs.

## M8 — Resource optimization

Add active-learning prioritization, cache, compaction, adaptive polling, local preprocessing, quota alarms and degraded-mode policies.

Exit: cloud outages do not break navigation; internal limits leave provider headroom.

## M9 — Production integration

Add mobile API adapter, compatibility negotiation, signed releases, stale-data handling and end-to-end feedback.

Exit: unavailable training service does not break navigation and all production changes are reversible.

## M10 — Continuous improvement

Add scheduled evaluation, regression issue creation, model/rule review, drift monitoring, source-quality decay detection and release reports.

Exit: learning stops when expected gain is below cost and regressions remain represented in tests.

## First implementation slice

1. Create directory boundaries.
2. Add shared event and control contracts.
3. Add release-state model.
4. Add local event journal.
5. Add CI contract checks.
6. Add dashboard wireframe and metric definitions.

This slice changes no routing behavior.
