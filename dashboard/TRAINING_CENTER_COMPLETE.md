# Training Center Complete

The complete dashboard brings all AI-training concerns into one navigable surface:

- overview and learning factor
- source quality and knowledge map
- experiment comparison and root-cause analysis
- training calendar and self-assessment
- approvals, canary, rollback and audit
- resource, FIFO and offline/cloud synchronization
- route/map explorer and version time travel
- AI dialogue and controlled actions

## API integration contract

Replace demonstration values with versioned resources:

- GET /api/training/overview
- GET /api/training/knowledge
- GET /api/training/experiments
- GET /api/training/operations
- GET /api/training/history
- POST /api/training/dialogue
- POST /api/training/actions/replay
- POST /api/training/actions/explain
- POST /api/training/actions/create-regression
- POST /api/training/actions/prepare-rollback

Every response must include schema_version, captured_at, sample_count, data_age, provenance and stale/offline state. The UI must never fabricate live values when the API is unavailable.
