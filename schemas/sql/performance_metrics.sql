CREATE TABLE IF NOT EXISTS performance_metrics (
    metric_id TEXT PRIMARY KEY,
    release_id TEXT NOT NULL,
    track TEXT NOT NULL,
    name TEXT NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    baseline_value DOUBLE PRECISION NOT NULL,
    threshold DOUBLE PRECISION NOT NULL,
    sample_count BIGINT NOT NULL CHECK (sample_count >= 0),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    critical BOOLEAN NOT NULL DEFAULT FALSE,
    regression BOOLEAN NOT NULL DEFAULT FALSE,
    evidence_ref TEXT
);

CREATE TABLE IF NOT EXISTS optimization_cycles (
    cycle_id TEXT PRIMARY KEY,
    track TEXT NOT NULL,
    status TEXT NOT NULL,
    baseline_release_id TEXT NOT NULL,
    candidate_release_id TEXT,
    expected_gain DOUBLE PRECISION NOT NULL CHECK (expected_gain >= 0),
    resource_cost DOUBLE PRECISION NOT NULL CHECK (resource_cost >= 0),
    regression_triggered BOOLEAN NOT NULL DEFAULT FALSE,
    repair_task_id TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS performance_gates (
    release_id TEXT PRIMARY KEY,
    safety_passed BOOLEAN NOT NULL,
    critical_metrics_passed BOOLEAN NOT NULL,
    resource_budget_passed BOOLEAN NOT NULL,
    reproducible BOOLEAN NOT NULL,
    rollback_ready BOOLEAN NOT NULL,
    approved BOOLEAN NOT NULL,
    reason TEXT NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS performance_metrics_release_track_idx
    ON performance_metrics (release_id, track, window_end);
