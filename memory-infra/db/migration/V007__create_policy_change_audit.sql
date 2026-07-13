-- V007 — Create policy_change_audit table
-- Records a full snapshot of a tenant's memory policy every time it is replaced, for governance
-- traceability (who tightened federation, when decay changed, etc.).
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE policy_change_audit;

CREATE TABLE IF NOT EXISTS policy_change_audit (
    id                            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                     TEXT         NOT NULL,
    decay_rate                    DOUBLE PRECISION NOT NULL,
    decay_after_days              INTEGER      NOT NULL,
    reinforcement_increment       DOUBLE PRECISION NOT NULL,
    archive_threshold             DOUBLE PRECISION NOT NULL,
    retention_days                INTEGER      NOT NULL,
    federation_enabled            BOOLEAN      NOT NULL,
    federation_max_summary_length INTEGER      NOT NULL,
    changed_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_policy_change_audit_tenant
    ON policy_change_audit (tenant_id, changed_at DESC);
