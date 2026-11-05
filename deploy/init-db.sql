-- Idempotent local initialization for the training platform.
INSERT INTO storage_config (id, max_bytes, reserved_headroom_bytes, warning_bytes)
VALUES (TRUE, 107374182400, 26843545600, 69793218560)
ON CONFLICT (id) DO NOTHING;
