# Training API bridge

Run locally with Python 3:

    python3 backend/training_api.py

The service listens on 127.0.0.1:8787 by default.

The dashboard adapter expects the API at the same origin. For a local preview,
serve the dashboard and proxy /api/training/* to this service. The service is
safe to run as an optional component: navigation remains independent.

Endpoints:

- GET /api/training/overview
- GET /api/training/knowledge
- GET /api/training/experiments
- GET /api/training/operations
- GET /api/training/history
- POST /api/training/dialogue
- POST /api/training/actions/{replay|explain|create-regression|prepare-rollback}

This first bridge keeps state in memory. The next persistence step can replace
TrainingState with the existing FIFO journal and cloud synchronization worker
without changing the dashboard contract.
