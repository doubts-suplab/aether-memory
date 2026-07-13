-- V005 — Create federation_audit table
-- Records every federation query (who asked, what for, when, how many results) — an audit of
-- the *query*, never of the matched memories' identities. Supports abuse detection and rate
-- accounting without weakening the privacy of federated content.
-- Lock risk: LOW (new table, no existing data)
-- Rollback: DROP TABLE federation_audit;

CREATE TABLE IF NOT EXISTS federation_audit (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    origin_tenant_id  TEXT         NOT NULL,
    query_text        TEXT         NOT NULL,
    memory_type       TEXT         NULL
                                   CHECK (memory_type IS NULL
                                          OR memory_type IN ('EPISODIC', 'SEMANTIC', 'PROCEDURAL', 'EMOTIONAL')),
    result_count      INTEGER      NOT NULL DEFAULT 0,
    local_only        BOOLEAN      NOT NULL DEFAULT FALSE,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_federation_audit_origin
    ON federation_audit (origin_tenant_id, occurred_at DESC);
