-- V008 — Create gdpr_erasure_audit table
-- Immutable proof-of-erasure record for GDPR right-to-erasure requests: what scope was erased,
-- how many active and archived memories were removed, and when. Written in the same transaction
-- as the erasure so the audit and the deletion are atomic.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE gdpr_erasure_audit;

CREATE TABLE IF NOT EXISTS gdpr_erasure_audit (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        TEXT         NOT NULL,
    team_id          TEXT         NULL,               -- NULL for tenant-wide erasure
    erasure_scope    TEXT         NOT NULL CHECK (erasure_scope IN ('TEAM', 'TENANT')),
    active_deleted   BIGINT       NOT NULL,
    archived_deleted BIGINT       NOT NULL,
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gdpr_erasure_audit_tenant
    ON gdpr_erasure_audit (tenant_id, occurred_at DESC);
