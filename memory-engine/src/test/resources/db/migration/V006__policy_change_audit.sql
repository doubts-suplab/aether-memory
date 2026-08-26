-- V006 — Governance & Policy: append-only policy-change audit log
-- Phase 3 (Governance & Policy). Records every accepted change to a tenant's memory policy — the
-- levers that govern retention, decay, and federation — so policy evolution is demonstrable after
-- the fact (accountability). Stores a bounded, human-readable delta only; no secrets, no content.
-- Lock risk: LOW (new table only)
-- Rollback: DROP TABLE policy_change_audit;

CREATE TABLE IF NOT EXISTS policy_change_audit (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      TEXT         NOT NULL,
    actor          TEXT         NULL,
    change_summary TEXT         NOT NULL DEFAULT '',
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Per-tenant history, newest first.
CREATE INDEX IF NOT EXISTS idx_policy_change_audit_tenant
    ON policy_change_audit (tenant_id, occurred_at DESC);
