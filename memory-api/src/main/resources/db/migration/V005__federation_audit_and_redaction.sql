-- V005 — Federation hardening: audit log + per-tenant redaction depth
-- Phase 2 (Federation). Records who queried the federation boundary and how much they received
-- (accountability), and lets each tenant configure how much of its content may leak in a federated
-- projection (redaction depth). No memory content or team identity is stored in the audit.
-- Lock risk: LOW (new table + additive column with a default)
-- Rollback: DROP TABLE federation_audit; ALTER TABLE memory_policies DROP COLUMN federation_summary_chars;

CREATE TABLE IF NOT EXISTS federation_audit (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    origin_tenant_id TEXT         NOT NULL,
    memory_type      TEXT         NULL
                                  CHECK (memory_type IS NULL OR memory_type IN
                                        ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    query_label      TEXT         NOT NULL DEFAULT '',
    result_count     INTEGER      NOT NULL DEFAULT 0 CHECK (result_count >= 0),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Recent federation activity, newest first / by origin.
CREATE INDEX IF NOT EXISTS idx_federation_audit_recent ON federation_audit (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_federation_audit_origin ON federation_audit (origin_tenant_id, occurred_at DESC);

-- Per-tenant redaction depth: max characters of this tenant's content exposed in a federated
-- projection (0 = fully redacted; capped at 280 = FederatedMemory.MAX_SUMMARY_LENGTH).
ALTER TABLE memory_policies
    ADD COLUMN IF NOT EXISTS federation_summary_chars INTEGER NOT NULL DEFAULT 280
        CHECK (federation_summary_chars >= 0 AND federation_summary_chars <= 280);
