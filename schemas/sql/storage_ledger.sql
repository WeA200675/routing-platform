CREATE TABLE IF NOT EXISTS storage_objects (
    object_id TEXT PRIMARY KEY,
    object_key TEXT NOT NULL UNIQUE,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_seq BIGINT NOT NULL UNIQUE,
    object_class TEXT NOT NULL,
    protected BOOLEAN NOT NULL DEFAULT FALSE,
    expired BOOLEAN NOT NULL DEFAULT FALSE,
    committed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS storage_reservations (
    reservation_id TEXT PRIMARY KEY,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS storage_config (
    id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id),
    max_bytes BIGINT NOT NULL CHECK (max_bytes > 0),
    reserved_headroom_bytes BIGINT NOT NULL CHECK (reserved_headroom_bytes > 0),
    warning_bytes BIGINT NOT NULL CHECK (warning_bytes > 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (reserved_headroom_bytes < max_bytes),
    CHECK (warning_bytes < max_bytes - reserved_headroom_bytes)
);

CREATE TABLE IF NOT EXISTS storage_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    provider_bytes BIGINT NOT NULL CHECK (provider_bytes >= 0),
    ledger_bytes BIGINT NOT NULL CHECK (ledger_bytes >= 0),
    in_flight_bytes BIGINT NOT NULL CHECK (in_flight_bytes >= 0),
    reconciled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS storage_objects_fifo_idx
    ON storage_objects (created_seq)
    WHERE protected = FALSE AND expired = FALSE;
