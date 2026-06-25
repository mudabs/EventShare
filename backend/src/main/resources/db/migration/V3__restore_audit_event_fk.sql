-- The audit_logs -> events foreign key was temporarily dropped during development
-- to work around a transaction-propagation bug. AuditService now writes in the
-- caller's transaction, so the FK is safe to enforce again. Idempotent so it works
-- on both fresh databases (FK present from V1) and the patched dev database.
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_event_id_fkey;
ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_event_id_fkey
    FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE SET NULL;
